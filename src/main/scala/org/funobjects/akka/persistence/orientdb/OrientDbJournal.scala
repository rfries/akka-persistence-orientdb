/*
 * Copyright 2015 Functional Objects, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.funobjects.akka.persistence.orientdb

import akka.actor.ActorLogging
import akka.persistence._
import akka.persistence.journal._
import akka.serialization._

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.index.{OCompositeKey, OIndex}
import com.orientechnologies.orient.core.metadata.schema.{OClass, OType}
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.typesafe.config.Config

import scala.collection.immutable.Seq
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * OrientDB Journal support for Akka Persistence
 */
class OrientDbJournal extends SyncWriteJournal with ActorLogging {

  val cfg: Config = context.system.settings.config
  val dbUrl = cfg.getString("funobjects-akka-orientdb-journal.db.url")

  val journalClass = "AkkaJournal"

  // property names
  val seq = "seq"
  val persistenceId = "persistenceId"
  val bytes = "bytes"

  val seqIndex = s"$journalClass.$persistenceId.$seq"

  val serializer = SerializationExtension(context.system)

  def repr(bytes: Array[Byte]): PersistentRepr = serializer.deserialize(bytes, classOf[PersistentRepr]).get
  def reprBytes(p: PersistentRepr): Array[Byte] = serializer.serialize(p).get

  // cached database state, initialized in preStart
  var db = checkDb()
  var index: OIndex[_] = _  // set by checkDb

  import context.dispatcher

  override def deleteMessagesTo(persistenceId: String, toSequenceNr: Long, permanent: Boolean): Unit = {
    OrientDbHelper.setupThreadContext(db)
    inTransaction {
      index.iterateEntriesMinor(key(persistenceId, toSequenceNr), true, true).foreach { oid =>
        if (permanent)
          db.delete(oid.getIdentity)
        else {
          val doc = oid.getRecord[ODocument]
          doc.field(bytes, mapSerialized(doc.field(bytes)) { _.update(deleted = true) } )
          doc.save()
        }
      }
    }
  }

  override def writeMessages(messages: Seq[PersistentRepr]): Unit = {
    OrientDbHelper.setupThreadContext(db)
    inTransaction {
      messages.foreach { msg =>
        db.save[ODocument](new ODocument(journalClass)
          .field(seq, msg.sequenceNr)
          .field(persistenceId, msg.persistenceId)
          .field(bytes, reprBytes(msg)))
      }
    }
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)
    (replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
      Future {
        val lower = key(persistenceId, fromSequenceNr)
        val upper = key(persistenceId, toSequenceNr)
        index.iterateEntriesBetween(lower, true, upper, true, true)
          .map ( oid => repr(oid.getRecord[ODocument].field(bytes)))
          .zipWithIndex
          .foreach { case (repr, n) => if (n < max) replayCallback(repr) }
      }
    }


  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = Future {
    OrientDbHelper.setupThreadContext(db)

    val q = new OSQLSynchQuery[ODocument]("select seq from AkkaJournal where persistenceId = ? order by seq desc limit 1")
    val res: java.util.List[ODocument] = db.command(q).execute(persistenceId)
    res.headOption.map(_.field("seq").asInstanceOf[Long]).getOrElse(0L)
  }

  @deprecated("writeConfirmations will be removed, since Channels will be removed.")
  override def writeConfirmations(confirmations: Seq[PersistentConfirmation]): Unit = {
    inTransaction {
      confirmations
        .map(conf => (key(conf.persistenceId, conf.sequenceNr), conf.channelId))
        .foreach { case (key, channelId) =>
          find(key).foreach { doc =>
            doc.field(bytes, mapSerialized(doc.field(bytes)) ( conf => conf.update(confirms = conf.confirms :+ channelId) ) )
            doc.save()
          }
        }
    }
  }

  @deprecated("deleteMessages will be removed.")
  override def deleteMessages(messageIds: Seq[PersistentId], permanent: Boolean): Unit = {
    inTransaction {
      find(messageIds.map(id => key(id.persistenceId, id.sequenceNr))).foreach { doc =>
        if (permanent)
          db.delete(doc.getIdentity)
        else {
          doc.field("bytes", mapSerialized(doc.field(bytes))(_.update(deleted = true)))
          doc.save()
        }
      }
    }
  }

  override def preStart(): Unit = {
    db = checkDb()
    super.preStart()

  }

  override def postStop(): Unit = {
    super.postStop()
    db.close()
  }

  private[orientdb] def checkDb(): ODatabaseDocumentTx = {

    // retrieve the schema
    val db = OrientDbHelper.openOrCreate(dbUrl, "admin", "admin")
    val schema = db.getMetadata.getSchema

    // create the DB class and index, if necessary
    val cls = Option(schema.getClass(journalClass)) getOrElse schema.createClass(journalClass)

    // add indexed properties to the schema (required for index creation)
    Option(cls.getProperty(seq)) getOrElse cls.createProperty(seq, OType.LONG)
    Option(cls.getProperty(persistenceId)) getOrElse cls.createProperty(persistenceId, OType.STRING)

    // create a unique index on the composite key of (persistentId, seq)
    index = Option(cls.getClassIndex(seqIndex)) getOrElse cls.createIndex(seqIndex, OClass.INDEX_TYPE.UNIQUE, persistenceId, seq)

    // make sure that everything ends up with right type
    assert(cls.getProperty(seq).getType == OType.LONG)
    assert(cls.getProperty(persistenceId).getType == OType.STRING)
    assert(cls.getIndexes.map(_.getName).contains(seqIndex))
    db
  }

  // execute the given code within a database transaction
  private[orientdb] def inTransaction(f: => Unit): Unit = {
    try {
      db.begin()
      f
      db.commit()
    } catch {
      case NonFatal(ex) => db.rollback(); throw ex
    }
  }

  // create a composite key
  private[orientdb] def key(persistenceId: String, seq: Long) = {
    val k = new OCompositeKey()
    k.addKey(persistenceId)
    k.addKey(seq)
    k
  }

  // create a partial key with persistenceId only
  private[orientdb] def partialKey(persistenceId: String) = {
    val k = new OCompositeKey()
    k.addKey(persistenceId)
    k
  }

  // transform a PersistentRepr (in serialized form)
  private[orientdb] def mapSerialized(bytes: Array[Byte])(transform: PersistentRepr => PersistentRepr): Array[Byte] =
    reprBytes(transform(repr(bytes)))

  private[orientdb] def find(keys: Seq[OCompositeKey]): Seq[ODocument] =
    index.iterateEntries(keys, true)
      .map { oid => oid.getRecord[ODocument] }
      .toList

  private[orientdb] def find(key: OCompositeKey): Option[ODocument] = find(Seq(key)).headOption
}