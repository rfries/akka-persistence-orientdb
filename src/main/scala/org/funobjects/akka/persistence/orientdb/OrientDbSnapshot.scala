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

import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.persistence.snapshot.SnapshotStore
import akka.serialization.SerializationExtension
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.index.OIndex
import com.orientechnologies.orient.core.metadata.schema.{OClass, OType}
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.collection.JavaConversions._
import scala.util.Try
import scala.util.control.NonFatal

case class Snap(o: Any)

/**
 * OrientDB snapshot support for Akka Persistence.
 */
class OrientDbSnapshot extends SnapshotStore {

  val cfg: Config = context.system.settings.config
  val dbUrl = cfg.getString("funobjects-akka-orientdb-snapshot.db.url")

  val snapshotClass = "AkkaSnapshot"

  // property names
  val persistenceId = "persistenceId"
  val seq = "seq"
  val timestamp = "timestamp"
  val bytes = "bytes"

  val seqIndexName = s"$snapshotClass.$persistenceId.$seq"
  val tsIndexName = s"$snapshotClass.$persistenceId.$timestamp"

  val serializer = SerializationExtension(context.system)

  def snap(bytes: Array[Byte]): Snap = serializer.deserialize(bytes, classOf[Snap]).get
  def snapBytes(o: Any): Array[Byte] = serializer.serialize(Snap(o)).get

  // cached database state, initialized in preStart
  var db: ODatabaseDocumentTx = _
  var seqIndex: OIndex[_] = _  // set by checkDb
  var tsIndex: OIndex[_] = _  // set by checkDb

  import context.dispatcher

  override def preStart(): Unit = {
    super.preStart()
    db = checkDb()
  }

  override def postStop(): Unit = {
    db.close()
    super.postStop()
  }

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    println(s"load $persistenceId $criteria")
    Future {
      OrientDbHelper.setupThreadContext(db)
      val q = new OSQLSynchQuery[ODocument]("select bytes from AkkaSnapshot where persistenceId = ? and seq <= ? and seq >= ? and timestamp <= ? and timestamp >= ?")
      val res: java.util.List[ODocument] = db
        .command(q)
        .execute(persistenceId, criteria.maxSequenceNr.asInstanceOf[AnyRef], criteria.minSequenceNr.asInstanceOf[AnyRef], criteria.maxTimestamp.asInstanceOf[AnyRef], criteria.minTimestamp.asInstanceOf[AnyRef])
      res.foreach { r =>
        println(s"res: $r ${r.toMap}")
      }
      res.headOption.map { doc =>
        SelectedSnapshot(
          SnapshotMetadata(persistenceId, doc.field[Long](seq), doc.field[Long](timestamp)), snap(doc.field(bytes))
        )
      }
    }
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    println(s"save $metadata ${bytes.length}")
    Future.fromTry {
      Try {
        OrientDbHelper.setupThreadContext(db)
        inTransaction(
          new ODocument(snapshotClass)
            .field(persistenceId, metadata.persistenceId)
            .field(seq, metadata.sequenceNr)
            .field(timestamp, metadata.timestamp)
            .field(bytes, snapBytes(snapshot))
            .save())
      }
    }
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    println(s"delete $metadata")
    Future.fromTry {
      Try {
        OrientDbHelper.setupThreadContext(db)
        inTransaction {
          val cmd = new OCommandSQL("delete from AkkaSnapshot where persistenceId = ? and seq = ? and timestamp = ?")
          val ret = db.command(cmd).execute(metadata.persistenceId, metadata.sequenceNr.asInstanceOf[AnyRef], metadata.timestamp.asInstanceOf[AnyRef])
        }
      }
    }
  }


  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    println(s"delete $persistenceId $criteria")
    Future.fromTry {
      Try {
        OrientDbHelper.setupThreadContext(db)
        inTransaction {
          val cmd = new OCommandSQL("delete from AkkaSnapshot where persistenceId = ? and seq < ? and timestamp < ?")
          val ret = db.command(cmd).execute(persistenceId, criteria.maxSequenceNr.asInstanceOf[AnyRef], criteria.maxTimestamp.asInstanceOf[AnyRef])
        }
      }
    }
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

  private[orientdb] def checkDb(): ODatabaseDocumentTx = {

    // retrieve the schema
    val db = OrientDbHelper.openOrCreate(dbUrl, "admin", "admin")
    val schema = db.getMetadata.getSchema

    // create the DB class and index, if necessary
    val cls = Option(schema.getClass(snapshotClass)) getOrElse schema.createClass(snapshotClass)

    // add indexed properties to the schema (required for index creation)
    Option(cls.getProperty(persistenceId)) getOrElse cls.createProperty(persistenceId, OType.STRING)
    Option(cls.getProperty(seq)) getOrElse cls.createProperty(seq, OType.LONG)
    Option(cls.getProperty(timestamp)) getOrElse cls.createProperty(timestamp, OType.LONG)
    Option(cls.getProperty(bytes)) getOrElse cls.createProperty(bytes, OType.BINARY)

    // create a unique index on the composite key of (persistentId, seq)
    seqIndex = Option(cls.getClassIndex(seqIndexName)) getOrElse cls.createIndex(seqIndexName, OClass.INDEX_TYPE.UNIQUE, persistenceId, seq)
    tsIndex = Option(cls.getClassIndex(tsIndexName)) getOrElse cls.createIndex(tsIndexName, OClass.INDEX_TYPE.NOTUNIQUE, persistenceId, timestamp)

    schema.save()

    // make sure that everything ends up with right type
    assert(cls.getProperty(persistenceId).getType == OType.STRING)
    assert(cls.getProperty(seq).getType == OType.LONG)
    assert(cls.getProperty(timestamp).getType == OType.LONG)
    assert(cls.getProperty(bytes).getType == OType.BINARY)
    assert(cls.getIndexes.map(_.getName).contains(seqIndexName))
    assert(cls.getIndexes.map(_.getName).contains(tsIndexName))
    db
  }
}
