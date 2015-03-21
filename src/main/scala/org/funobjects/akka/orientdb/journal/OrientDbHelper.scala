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

package org.funobjects.akka.orientdb.journal

import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

/**
 * Useful common code for dealing with OrientDb
 */
object OrientDbHelper {

  /**
   * Creates a new, empty database, deleting all data in the current database if it currently exists.
   */
  def newDatabase(url: String, user: String, pass: String): ODatabaseDocumentTx = {
    val db = removeDatabase(url, user, pass)
    // create leaves db open, (created with default user/pass)
    db.create()
  }

  /**
   * Deletes the database if it exists,
   */
  def removeDatabase(db: ODatabaseDocumentTx, user: String, pass: String): ODatabaseDocumentTx = {
    if (db.exists()) {
      if (db.isClosed) {
        db.open(user, pass)
      }
      // must be open to drop, and leaves db closed (and non-existent)
      db.drop()
    }
    db
  }

  def removeDatabase(url: String, user: String, pass: String): ODatabaseDocumentTx = {
    val db = new ODatabaseDocumentTx(url)
    removeDatabase(db, user, pass)
  }

  def openOrCreate(url: String, user: String, pass: String): ODatabaseDocumentTx = {
    val db = new ODatabaseDocumentTx(url)
    try {
      // bad url will cause exception is db.exists(), not in construction
      if (db.exists()) {
        db.open(user, pass)
      } else {
        db.create()
      }
      // if we get here, the db is open, and created if necessary
      db
    } catch {
      // make sure we don't leak connections on error
      case ex: Exception =>
        if (!db.isClosed) {
          db.close()
        }
        throw ex
    }
  }

  def setupThreadContext(db: ODatabaseDocumentTx): Unit = ODatabaseRecordThreadLocal.INSTANCE.set(db)

  def dbState(label: String, db: ODatabaseDocumentTx): String = s"$label.isClosed() = ${db.isClosed}, $label.exists() = ${db.exists()}"
}
