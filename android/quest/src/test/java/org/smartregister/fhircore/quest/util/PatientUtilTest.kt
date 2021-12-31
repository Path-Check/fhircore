/*
 * Copyright 2021 Ona Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.smartregister.fhircore.quest.util

import com.google.android.fhir.FhirEngine
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.mockk
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.Enumerations
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.smartregister.fhircore.engine.auth.AccountAuthenticator
import org.smartregister.fhircore.engine.configuration.ConfigurationRegistry
import org.smartregister.fhircore.quest.configuration.view.Code
import org.smartregister.fhircore.quest.configuration.view.DynamicColor
import org.smartregister.fhircore.quest.configuration.view.Filter
import org.smartregister.fhircore.quest.robolectric.RobolectricTest

@HiltAndroidTest
class PatientUtilTest : RobolectricTest() {

  @Inject lateinit var configurationRegistry: ConfigurationRegistry
  @Inject lateinit var accountAuthenticator: AccountAuthenticator

  @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

  @Before
  fun setUp() {
    hiltRule.inject()
    configurationRegistry.loadAppConfigurations(
      appId = "g6pd",
      accountAuthenticator = accountAuthenticator
    ) {}
  }

  @Test
  fun testLoadAdditionalDataShouldReturnExpectedData() {

    val fhirEngine =
      mockk<FhirEngine> { coEvery { search<Condition>(any()) } returns getConditions() }

    val data = runBlocking { loadAdditionalData("", configurationRegistry, fhirEngine) }

    Assert.assertEquals(1, data.size)
    with(data[0]) {
      Assert.assertNull(label)
      Assert.assertEquals(" G6PD Status - ", valuePrefix)
      Assert.assertEquals("Normal", value)
      Assert.assertEquals("Normal", value)
      Assert.assertEquals("#00a000", properties?.value?.color)
    }
  }

  @Test
  fun testPropertiesMapping() {

    val filter =
      Filter(
        resourceType = Enumerations.ResourceType.CONDITION,
        key = "code",
        valueType = Enumerations.DataType.CODEABLECONCEPT,
        valueCoding = Code(),
        dynamicColors = listOf(DynamicColor("Normal", "#00FF00"))
      )

    var properties = runBlocking { propertiesMapping("Normal", filter) }

    Assert.assertNull(properties.label)
    Assert.assertNull(properties.value?.textSize)
    Assert.assertEquals("#00FF00", properties.value?.color)

    properties = runBlocking { propertiesMapping("Deficient", filter) }

    Assert.assertNull(properties.label)
    Assert.assertNull(properties.value?.textSize)
    Assert.assertNull(properties.value?.color)
  }

  private fun getConditions(): List<Condition> {
    return listOf(
      Condition().apply {
        recordedDate = Date()
        code =
          CodeableConcept().apply {
            addCoding().apply {
              system = "http://snomed.info/sct"
              code = "260372006"
              display = "Normal"
            }
          }
      }
    )
  }
}
