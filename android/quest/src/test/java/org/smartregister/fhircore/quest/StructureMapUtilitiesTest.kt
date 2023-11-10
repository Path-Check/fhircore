/*
 * Copyright 2021-2023 Ona Systems, Inc
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

package org.smartregister.fhircore.quest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import ca.uhn.fhir.parser.IParser
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.knowledge.KnowledgeManager
import com.google.android.fhir.workflow.FhirOperator
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import kotlin.reflect.KSuspendFunction1
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.hl7.fhir.exceptions.FHIRException
import org.hl7.fhir.r4.context.SimpleWorkerContext
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CanonicalType
import org.hl7.fhir.r4.model.Immunization
import org.hl7.fhir.r4.model.Library
import org.hl7.fhir.r4.model.MetadataResource
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Parameters
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.RelatedPerson
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager
import org.junit.Assert
import org.junit.Test
import org.smartregister.fhircore.engine.util.extension.getCustomJsonParser
import org.smartregister.fhircore.engine.util.helper.TransformSupportServices
import org.smartregister.fhircore.quest.robolectric.RobolectricTest

/**
 * Provides a playground for quickly testing and authoring questionnaire.json and the respective
 * StructureMap
 *
 * This should be removed at a later point once we have a more clear way of doing this
 */
class StructureMapUtilitiesTest : RobolectricTest() {

  @Inject lateinit var fhirEngine: FhirEngine
  private val context: Context = ApplicationProvider.getApplicationContext<Context>()
  private val knowledgeManager = KnowledgeManager.create(context)
  private val fhirContext: FhirContext = FhirContext.forCached(FhirVersionEnum.R4)
  private val jsonParser = fhirContext.getCustomJsonParser()
  private val xmlParser = fhirContext.newXmlParser()

  @Test
  fun `perform family extraction`() {
    val registrationQuestionnaireResponseString: String =
      "content/general/family/questionnaire-response-standard.json".readFile()
    val registrationStructureMap = "content/general/family/family-registration.map".readFile()
    val packageCacheManager = FilesystemPackageCacheManager(true)
    val contextR4 =
      SimpleWorkerContext.fromPackage(packageCacheManager.loadPackage("hl7.fhir.r4.core", "4.0.1"))
        .apply {
          setExpansionProfile(Parameters())
          isCanRunWithoutTerminology = true
        }
    val transformSupportServices = TransformSupportServices(contextR4)
    val structureMapUtilities =
      org.hl7.fhir.r4.utils.StructureMapUtilities(contextR4, transformSupportServices)
    val structureMap =
      structureMapUtilities.parse(registrationStructureMap, "eCBIS Family Registration")
    val iParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val targetResource = Bundle()
    val baseElement =
      iParser.parseResource(
        QuestionnaireResponse::class.java,
        registrationQuestionnaireResponseString,
      )

    structureMapUtilities.transform(contextR4, baseElement, structureMap, targetResource)

    Assert.assertEquals(2, targetResource.entry.size)
    Assert.assertEquals("Group", targetResource.entry[0].resource.resourceType.toString())
    Assert.assertEquals("Encounter", targetResource.entry[1].resource.resourceType.toString())
  }

  @Test
  fun `perform disease extraction`() {
    val immunizationQuestionnaireResponseString: String =
      "content/general/disease-registration-resources/questionnaire_response.json".readFile()
    val immunizationStructureMap =
      "content/general/disease-registration-resources/structure-map.txt".readFile()
    val packageCacheManager = FilesystemPackageCacheManager(true)
    val contextR4 =
      SimpleWorkerContext.fromPackage(packageCacheManager.loadPackage("hl7.fhir.r4.core", "4.0.1"))
        .apply {
          setExpansionProfile(Parameters())
          isCanRunWithoutTerminology = true
        }
    val transformSupportServices = TransformSupportServices(contextR4)
    val structureMapUtilities =
      org.hl7.fhir.r4.utils.StructureMapUtilities(contextR4, transformSupportServices)
    val structureMap =
      structureMapUtilities.parse(immunizationStructureMap, "eCBIS Disease Registration")
    val iParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val targetResource = Bundle()
    val baseElement =
      iParser.parseResource(
        QuestionnaireResponse::class.java,
        immunizationQuestionnaireResponseString,
      )

    structureMapUtilities.transform(contextR4, baseElement, structureMap, targetResource)

    Assert.assertEquals(7, targetResource.entry.size)
    Assert.assertEquals("Condition", targetResource.entry[0].resource.resourceType.toString())
    Assert.assertEquals("Condition", targetResource.entry[1].resource.resourceType.toString())
  }

  @Test
  @kotlinx.coroutines.ExperimentalCoroutinesApi
  fun `populate immunization Questionnaire`() {
    val patientJson = "content/eir/immunization/patient.json".readFile()
    val immunizationJson = "content/eir/immunization/immunization-1.json".readFile()
    val immunizationStructureMap = "content/eir/immunization/structure-map.txt".readFile()
    val questionnaireJson = "content/eir/immunization/questionnaire.json".readFile()
    val packageCacheManager = FilesystemPackageCacheManager(true)
    val contextR4 =
      SimpleWorkerContext.fromPackage(packageCacheManager.loadPackage("hl7.fhir.r4.core", "4.0.1"))
        .apply {
          setExpansionProfile(Parameters())
          isCanRunWithoutTerminology = true
        }
    val transformSupportServices = TransformSupportServices(contextR4)
    val structureMapUtilities =
      org.hl7.fhir.r4.utils.StructureMapUtilities(contextR4, transformSupportServices)
    val structureMap =
      structureMapUtilities.parse(immunizationStructureMap, "ImmunizationRegistration")
    val iParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val targetResource = Bundle()
    val patient = iParser.parseResource(Patient::class.java, patientJson)
    val immunization = iParser.parseResource(Immunization::class.java, immunizationJson)
    val questionnaire = iParser.parseResource(Questionnaire::class.java, questionnaireJson)
    val questionnaireResponse: QuestionnaireResponse

    runBlocking {
      questionnaireResponse =
        ResourceMapper.populate(
          questionnaire,
          mapOf(
            ResourceType.Patient.name.lowercase() to patient,
            ResourceType.Immunization.name.lowercase() to immunization,
          ),
        )
    }

    structureMapUtilities.transform(contextR4, questionnaireResponse, structureMap, targetResource)

    Assert.assertEquals(2, targetResource.entry.size)
    Assert.assertEquals("Encounter", targetResource.entry[0].resource.resourceType.toString())
    Assert.assertEquals("Immunization", targetResource.entry[1].resource.resourceType.toString())
  }

  @Test
  @kotlinx.coroutines.ExperimentalCoroutinesApi
  fun `populate patient registration Questionnaire and extract Resources`() {
    val patientRegistrationQuestionnaire =
      "patient-registration-questionnaire/questionnaire.json".readFile()
    val patientRegistrationStructureMap =
      "patient-registration-questionnaire/structure-map.txt".readFile()
    val relatedPersonJson = "patient-registration-questionnaire/related-person.json".readFile()
    val patientJson = "patient-registration-questionnaire/sample/patient.json".readFile()
    val iParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val questionnaire =
      iParser.parseResource(Questionnaire::class.java, patientRegistrationQuestionnaire)
    val patient = iParser.parseResource(Patient::class.java, patientJson)
    val relatedPerson = iParser.parseResource(RelatedPerson::class.java, relatedPersonJson)
    var questionnaireResponse: QuestionnaireResponse

    runBlocking {
      questionnaireResponse =
        ResourceMapper.populate(
          questionnaire,
          mapOf(
            ResourceType.Patient.name.lowercase() to patient,
            ResourceType.RelatedPerson.name.lowercase() to relatedPerson,
          ),
        )
    }

    val packageCacheManager = FilesystemPackageCacheManager(true)
    val contextR4 =
      SimpleWorkerContext.fromPackage(packageCacheManager.loadPackage("hl7.fhir.r4.core", "4.0.1"))
        .apply {
          setExpansionProfile(Parameters())
          isCanRunWithoutTerminology = true
        }
    val transformSupportServices = TransformSupportServices(contextR4)
    val structureMapUtilities =
      org.hl7.fhir.r4.utils.StructureMapUtilities(contextR4, transformSupportServices)
    val structureMap =
      structureMapUtilities.parse(patientRegistrationStructureMap, "PatientRegistration")
    val targetResource = Bundle()

    structureMapUtilities.transform(contextR4, questionnaireResponse, structureMap, targetResource)

    Assert.assertEquals(1, targetResource.entry.size)
    Assert.assertEquals("Patient", targetResource.entry[0].resource.resourceType.toString())
  }

  @Test
  @kotlinx.coroutines.ExperimentalCoroutinesApi
  fun `populate adverse event Questionnaire and extract Resources`() {
    val adverseEventQuestionnaire = "content/eir/adverse-event/questionnaire.json".readFile()
    val adverseEventStructureMap = "content/eir/adverse-event/structure-map.txt".readFile()
    val immunizationJson = "content/eir/adverse-event/immunization.json".readFile()
    val iParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val questionnaire = iParser.parseResource(Questionnaire::class.java, adverseEventQuestionnaire)
    val immunization = iParser.parseResource(Immunization::class.java, immunizationJson)
    var questionnaireResponse: QuestionnaireResponse

    runBlocking {
      questionnaireResponse =
        ResourceMapper.populate(
          questionnaire,
          mapOf(
            ResourceType.Immunization.name.lowercase() to immunization,
            ResourceType.Patient.name.lowercase() to Patient(),
          ),
        )
    }

    val packageCacheManager = FilesystemPackageCacheManager(true)
    val contextR4 =
      SimpleWorkerContext.fromPackage(packageCacheManager.loadPackage("hl7.fhir.r4.core", "4.0.1"))
        .apply {
          setExpansionProfile(Parameters())
          isCanRunWithoutTerminology = true
        }
    val transformSupportServices = TransformSupportServices(contextR4)
    val structureMapUtilities =
      org.hl7.fhir.r4.utils.StructureMapUtilities(contextR4, transformSupportServices)
    val structureMap = structureMapUtilities.parse(adverseEventStructureMap, "AdverseEvent")
    val targetResource = Bundle()

    structureMapUtilities.transform(contextR4, questionnaireResponse, structureMap, targetResource)

    Assert.assertEquals(2, targetResource.entry.size)
    Assert.assertEquals("Immunization", targetResource.entry[0].resource.resourceType.toString())
    Assert.assertEquals("Observation", targetResource.entry[1].resource.resourceType.toString())
  }

  @Test
  fun `convert StructureMap to JSON`() {
    val patientRegistrationStructureMap =
      "patient-registration-questionnaire/structure-map.txt".readFile()
    val packageCacheManager = FilesystemPackageCacheManager(true)
    val contextR4 =
      SimpleWorkerContext.fromPackage(packageCacheManager.loadPackage("hl7.fhir.r4.core", "4.0.1"))
        .apply { isCanRunWithoutTerminology = true }
    val structureMapUtilities = org.hl7.fhir.r4.utils.StructureMapUtilities(contextR4)
    val structureMap =
      structureMapUtilities.parse(patientRegistrationStructureMap, "PatientRegistration")
    val iParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val mapString = iParser.encodeResourceToString(structureMap)

    Assert.assertNotNull(mapString)
  }

  @Test
  fun `perform extraction from patient registration Questionnaire`() {
    val patientRegistrationQuestionnaireResponse =
      "patient-registration-questionnaire/questionnaire-response.json".readFile()
    val patientRegistrationStructureMap =
      "patient-registration-questionnaire/structure-map.txt".readFile()
    val packageCacheManager = FilesystemPackageCacheManager(true)
    val contextR4 =
      SimpleWorkerContext.fromPackage(packageCacheManager.loadPackage("hl7.fhir.r4.core", "4.0.1"))
        .apply {
          setExpansionProfile(Parameters())
          isCanRunWithoutTerminology = true
        }
    val transformSupportServices = TransformSupportServices(contextR4)
    val structureMapUtilities =
      org.hl7.fhir.r4.utils.StructureMapUtilities(contextR4, transformSupportServices)
    val structureMap =
      structureMapUtilities.parse(patientRegistrationStructureMap, "PatientRegistration")
    val iParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val targetResource = Bundle()
    val baseElement =
      iParser.parseResource(
        QuestionnaireResponse::class.java,
        patientRegistrationQuestionnaireResponse,
      )
    structureMapUtilities.transform(contextR4, baseElement, structureMap, targetResource)

    Assert.assertEquals(2, targetResource.entry.size)
    Assert.assertEquals("Patient", targetResource.entry[0].resource.resourceType.toString())
    Assert.assertEquals("Condition", targetResource.entry[1].resource.resourceType.toString())
  }

  @Test
  fun `perform extraction from adverse event Questionnaire`() {
    val adverseEventQuestionnaireResponse =
      "content/eir/adverse-event/questionnaire-response.json".readFile()
    val adverseEventStructureMap = "content/eir/adverse-event/structure-map.txt".readFile()
    val packageCacheManager = FilesystemPackageCacheManager(true)
    val contextR4 =
      SimpleWorkerContext.fromPackage(packageCacheManager.loadPackage("hl7.fhir.r4.core", "4.0.1"))
        .apply {
          setExpansionProfile(Parameters())
          isCanRunWithoutTerminology = true
        }
    val transformSupportServices = TransformSupportServices(contextR4)
    val structureMapUtilities =
      org.hl7.fhir.r4.utils.StructureMapUtilities(contextR4, transformSupportServices)
    val structureMap = structureMapUtilities.parse(adverseEventStructureMap, "AdverseEvent")
    val iParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val targetResource = Bundle()

    val baseElement =
      iParser.parseResource(QuestionnaireResponse::class.java, adverseEventQuestionnaireResponse)

    structureMapUtilities.transform(contextR4, baseElement, structureMap, targetResource)

    Assert.assertEquals(2, targetResource.entry.size)
    Assert.assertEquals("Immunization", targetResource.entry[0].resource.resourceType.toString())
    Assert.assertEquals("Observation", targetResource.entry[1].resource.resourceType.toString())
  }

  @Test
  fun `perform extraction from  vital signs metric Questionnaire`() {
    val vitalSignQuestionnaireResponse =
      "content/anc/vital-signs/metric/questionnaire-response-pulse-rate.json".readFile()
    val vitalSignStructureMap = "content/anc/vital-signs/metric/structure-map.txt".readFile()
    val packageCacheManager = FilesystemPackageCacheManager(true)
    val contextR4 =
      SimpleWorkerContext.fromPackage(packageCacheManager.loadPackage("hl7.fhir.r4.core", "4.0.1"))
        .apply {
          setExpansionProfile(Parameters())
          isCanRunWithoutTerminology = true
        }
    val transformSupportServices = TransformSupportServices(contextR4)
    val structureMapUtilities =
      org.hl7.fhir.r4.utils.StructureMapUtilities(contextR4, transformSupportServices)
    val structureMap = structureMapUtilities.parse(vitalSignStructureMap, "VitalSigns")
    val iParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val targetResource = Bundle()
    val baseElement =
      iParser.parseResource(QuestionnaireResponse::class.java, vitalSignQuestionnaireResponse)

    structureMapUtilities.transform(contextR4, baseElement, structureMap, targetResource)

    Assert.assertEquals(2, targetResource.entry.size)
    Assert.assertEquals("Encounter", targetResource.entry[0].resource.resourceType.toString())
    Assert.assertEquals("Observation", targetResource.entry[1].resource.resourceType.toString())
  }

  @Test
  fun `perform extraction from  vital signs standard Questionnaire`() {
    val vitalSignQuestionnaireResponse =
      "content/anc/vital-signs/standard/questionnaire-response-pulse-rate.json".readFile()
    val vitalSignStructureMap = "content/anc/vital-signs/standard/structure-map.txt".readFile()

    val packageCacheManager = FilesystemPackageCacheManager(true)
    val contextR4 =
      SimpleWorkerContext.fromPackage(packageCacheManager.loadPackage("hl7.fhir.r4.core", "4.0.1"))
        .apply {
          setExpansionProfile(Parameters())
          isCanRunWithoutTerminology = true
        }
    val transformSupportServices = TransformSupportServices(contextR4)
    val structureMapUtilities =
      org.hl7.fhir.r4.utils.StructureMapUtilities(contextR4, transformSupportServices)
    val structureMap = structureMapUtilities.parse(vitalSignStructureMap, "VitalSigns")
    val iParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val targetResource = Bundle()
    val baseElement =
      iParser.parseResource(QuestionnaireResponse::class.java, vitalSignQuestionnaireResponse)

    structureMapUtilities.transform(contextR4, baseElement, structureMap, targetResource)

    Assert.assertEquals(2, targetResource.entry.size)
    Assert.assertEquals("Encounter", targetResource.entry[0].resource.resourceType.toString())
    Assert.assertEquals("Observation", targetResource.entry[1].resource.resourceType.toString())
  }

  @Test
  fun `perform location extraction`() {
    val locationQuestionnaireResponseString: String =
      "content/general/location/location-response-sample.json".readFile()
    val locationStructureMap = "content/general/location/location-structure-map.txt".readFile()
    val packageCacheManager = FilesystemPackageCacheManager(true)
    val contextR4 =
      SimpleWorkerContext.fromPackage(packageCacheManager.loadPackage("hl7.fhir.r4.core", "4.0.1"))
        .apply {
          setExpansionProfile(Parameters())
          isCanRunWithoutTerminology = true
        }
    val transformSupportServices = TransformSupportServices(contextR4)
    val structureMapUtilities =
      org.hl7.fhir.r4.utils.StructureMapUtilities(contextR4, transformSupportServices)
    val structureMap = structureMapUtilities.parse(locationStructureMap, "LocationRegistration")
    val iParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val targetResource = Bundle()
    val baseElement =
      iParser.parseResource(QuestionnaireResponse::class.java, locationQuestionnaireResponseString)

    structureMapUtilities.transform(contextR4, baseElement, structureMap, targetResource)

    Assert.assertEquals(1, targetResource.entry.size)
    Assert.assertEquals("Location", targetResource.entry[0].resource.resourceType.toString())
  }

  @Test
  fun `perform supply chain snapshot observation`() {
    val physicalInventoryCountQuestionnaireResponseString: String =
      "content/general/supply-chain/questionnaire-response-standard.json".readFile()
    val physicalInventoryCountStructureMap =
      "content/general/supply-chain/physical_inventory_count_and_stock.map".readFile()
    val packageCacheManager = FilesystemPackageCacheManager(true)
    val contextR4 =
      SimpleWorkerContext.fromPackage(packageCacheManager.loadPackage("hl7.fhir.r4.core", "4.0.1"))
        .apply {
          setExpansionProfile(Parameters())
          isCanRunWithoutTerminology = true
        }
    val transformSupportServices = TransformSupportServices(contextR4)
    val structureMapUtilities =
      org.hl7.fhir.r4.utils.StructureMapUtilities(contextR4, transformSupportServices)
    val structureMap =
      structureMapUtilities.parse(
        physicalInventoryCountStructureMap,
        "Physical Inventory Count and Stock Supply",
      )
    val iParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val targetResource = Bundle()
    val baseElement =
      iParser.parseResource(
        QuestionnaireResponse::class.java,
        physicalInventoryCountQuestionnaireResponseString,
      )

    structureMapUtilities.transform(contextR4, baseElement, structureMap, targetResource)

    // for some weird reason, the `entry` has 8 resources instead of 7. The 1st resource is blank.
    Assert.assertTrue(targetResource.entry.size == 9)
    Assert.assertTrue(targetResource.entry[2].resource is Observation)

    val observation = targetResource.entry[7].resource as Observation
    Assert.assertTrue(observation.code.text == "under-reporting")
  }

  @Test
  fun `perform extraction from  pregnancy outcome Questionnaire`() {
    val vitalSignQuestionnaireResponse =
      "content/anc/preg-outcome/questionnaire-response.json".readFile()
    val vitalSignStructureMap = "content/anc/preg-outcome/structure-map.txt".readFile()
    val packageCacheManager = FilesystemPackageCacheManager(true)
    val contextR4 =
      SimpleWorkerContext.fromPackage(packageCacheManager.loadPackage("hl7.fhir.r4.core", "4.0.1"))
        .apply {
          setExpansionProfile(Parameters())
          isCanRunWithoutTerminology = true
        }
    val transformSupportServices = TransformSupportServices(contextR4)
    val structureMapUtilities =
      org.hl7.fhir.r4.utils.StructureMapUtilities(contextR4, transformSupportServices)
    val structureMap =
      structureMapUtilities.parse(vitalSignStructureMap, "PregnancyOutcomeRegistration")
    val iParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val targetResource = Bundle()
    val baseElement =
      iParser.parseResource(QuestionnaireResponse::class.java, vitalSignQuestionnaireResponse)

    structureMapUtilities.transform(contextR4, baseElement, structureMap, targetResource)

    Assert.assertTrue(targetResource.entry.size > 10)
    val taskList =
      targetResource.entry.filter {
        it.resource.resourceType != null && it.resource.resourceType == ResourceType.Task
      }
    Assert.assertTrue(taskList.size == 10)
  }

  @Test(expected = FHIRException::class)
  fun `perform extraction for patient registration`() {
    val locationQuestionnaireResponseString: String =
      "content/general/who-eir/patient_registration_questionnaire_response.json".readFile()
    val locationStructureMap =
      "content/general/who-eir/patient_registration_structure_map.txt".readFile()
    val packageCacheManager = FilesystemPackageCacheManager(true)
    val contextR4 =
      SimpleWorkerContext.fromPackage(packageCacheManager.loadPackage("hl7.fhir.r4.core", "4.0.1"))
        .apply {
          setExpansionProfile(Parameters())
          isCanRunWithoutTerminology = true
        }
    val transformSupportServices = TransformSupportServices(contextR4)
    val structureMapUtilities =
      org.hl7.fhir.r4.utils.StructureMapUtilities(contextR4, transformSupportServices)
    val structureMap = structureMapUtilities.parse(locationStructureMap, "IMMZ-C-QRToPatient")
    val iParser: IParser = FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
    val targetResource = Bundle()
    val baseElement =
      iParser.parseResource(QuestionnaireResponse::class.java, locationQuestionnaireResponseString)

    structureMapUtilities.transform(contextR4, baseElement, structureMap, targetResource)

    Assert.assertEquals(2, targetResource.entry.size)
    Assert.assertEquals("Patient", targetResource.entry[0].resource.resourceType.toString())
    Assert.assertEquals("Condition", targetResource.entry[0].resource.resourceType.toString())
  }

  @Test
  fun generateMeaslesCarePlan() = runTest {
    loadFile("/content/general/who-eir/measles-immunizations/FHIRCommon.json", ::installToIgManager)
    loadFile(
      "/content/general/who-eir/measles-immunizations/FHIRHelpers.json",
      ::installToIgManager,
    )
    loadFile("/content/general/who-eir/measles-immunizations/IMMZCommon.json", ::installToIgManager)
    loadFile(
      "/content/general/who-eir/measles-immunizations/IMMZCommonIzDataElements.json",
      ::installToIgManager,
    )
    loadFile(
      "/content/general/who-eir/measles-immunizations/IMMZConcepts.json",
      ::installToIgManager,
    )
    loadFile("/content/general/who-eir/measles-immunizations/IMMZConfig.json", ::installToIgManager)
    loadFile(
      "/content/general/who-eir/measles-immunizations/IMMZD2DTMeasles.json",
      ::installToIgManager,
    )
    loadFile(
      "/content/general/who-eir/measles-immunizations/IMMZIndicatorCommon.json",
      ::installToIgManager,
    )
    loadFile(
      "/content/general/who-eir/measles-immunizations/IMMZINDMeasles.json",
      ::installToIgManager,
    )
    loadFile(
      "/content/general/who-eir/measles-immunizations/IMMZVaccineLibrary.json",
      ::installToIgManager,
    )
    loadFile(
      "/content/general/who-eir/measles-immunizations/ActivityDefinition-IMMZD2DTMeaslesMR.json",
      ::installToIgManager,
    )
    loadFile(
      "/content/general/who-eir/measles-immunizations/PlanDefinition-IMMZD2DTMeasles.json",
      ::installToIgManager,
    )
    loadFile("/content/general/who-eir/measles-immunizations/WHOCommon.json", ::installToIgManager)
    loadFile(
      "/content/general/who-eir/measles-immunizations/WHOConcepts.json",
      ::installToIgManager,
    )
    loadFile(
      "/content/general/who-eir/measles-immunizations/ValueSet-HIVstatus-values.json",
      ::installToIgManager,
    )

    loadFile(
      "/content/general/who-eir/measles-immunizations/IMMZ-Patient-NoVaxeninfant-f.json",
      ::importToFhirEngine,
    )
    loadFile(
      "/content/general/who-eir/measles-immunizations/birthweightnormal-NoVaxeninfant-f.json",
      ::importToFhirEngine,
    )

    val fhirOperator =
      FhirOperator.Builder(context)
        .fhirEngine(fhirEngine)
        .fhirContext(fhirContext)
        .knowledgeManager(knowledgeManager)
        .build()

    val carePlan =
      fhirOperator.generateCarePlan(
        planDefinition =
          CanonicalType(
            "http://fhir.org/guides/who/smart-immunization/PlanDefinition/IMMZD2DTMeasles",
          ),
        subject = "Patient/IMMZ-Patient-NoVaxeninfant-f",
      )

    println(jsonParser.encodeResourceToString(carePlan))

    assertNotNull(carePlan)
  }

  private suspend fun loadFile(path: String, importFunction: KSuspendFunction1<Resource, Unit>) {
    val resource =
      if (path.endsWith(suffix = ".xml")) {
        xmlParser.parseResource(open(path)) as Resource
      } else if (path.endsWith(".json")) {
        jsonParser.parseResource(open(path)) as Resource
      } else if (path.endsWith(".cql")) {
        toFhirLibrary(open(path))
      } else {
        throw IllegalArgumentException("Only xml and json and cql files are supported")
      }
    loadResource(resource, importFunction)
  }

  private suspend fun importToFhirEngine(resource: Resource) {
    fhirEngine.create(resource)
  }

  private suspend fun installToIgManager(resource: Resource) {
    knowledgeManager.install(writeToFile(resource))
  }

  private suspend fun loadResource(
    resource: Resource,
    importFunction: KSuspendFunction1<Resource, Unit>,
  ) {
    when (resource.resourceType) {
      ResourceType.Bundle -> loadBundle(resource as Bundle, importFunction)
      else -> importFunction(resource)
    }
  }

  private fun open(path: String) = javaClass.getResourceAsStream(path)!!

  private suspend fun loadBundle(
    bundle: Bundle,
    importFunction: KSuspendFunction1<Resource, Unit>,
  ) {
    for (entry in bundle.entry) {
      val resource = entry.resource
      loadResource(resource, importFunction)
    }
  }

  private fun writeToFile(resource: Resource): File {
    val fileName =
      if (resource is MetadataResource && resource.name != null) {
        resource.name
      } else {
        resource.idElement.idPart
      }
    return File(context.filesDir, fileName).apply {
      writeText(jsonParser.encodeResourceToString(resource))
    }
  }

  private fun toFhirLibrary(cql: InputStream): Library {
    // return CqlBuilder.compileAndBuild(cql)
    // TODO added only for temp purpose
    return Library()
  }
}
