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

import akka.persistence.journal.JournalSpec
import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory

/**
 * Created by rgf on 3/19/15.
 */
class OrientDbSnapshotSpec extends SnapshotStoreSpec(OrientDbSnapshotSpec.config) {

  val dbUrl = "plocal:testSnapshot"

  override def beforeAll(): Unit = {
    OrientDbHelper.removeDatabase(dbUrl, "admin", "admin")
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

}

object OrientDbSnapshotSpec {
  val config = ConfigFactory.parseString(
    s"""
    akka.persistence.snapshot-store.plugin = "funobjects-akka-orientdb-snapshot"
    funobjects-akka-orientdb-snapshot.db.url = "plocal:testSnapshot"
    """)
}