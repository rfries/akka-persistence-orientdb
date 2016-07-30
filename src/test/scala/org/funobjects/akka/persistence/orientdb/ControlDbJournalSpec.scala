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

import akka.persistence.CapabilityFlag
import akka.persistence.journal.{JournalPerfSpec, JournalSpec}
import com.typesafe.config.ConfigFactory

/**
 * Created by rgf on 3/19/15.
 */
class ControlDbJournalSpec extends JournalPerfSpec(ControlDbJournalSpec.config) {

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = CapabilityFlag.on
}

object ControlDbJournalSpec {
  val config = ConfigFactory.parseString(
    s"""
    akka.persistence.journal.plugin = "akka.persistence.journal.leveldb"
    """)
}