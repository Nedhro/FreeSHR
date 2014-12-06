package org.freeshr.interfaces.encounter.ws;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.freeshr.application.fhir.EncounterBundle;
import org.freeshr.config.SHRConfig;
import org.freeshr.config.SHREnvironmentMock;
import org.freeshr.config.SHRProperties;
import org.freeshr.domain.model.Facility;
import org.freeshr.domain.model.patient.Patient;
import org.freeshr.infrastructure.persistence.EncounterRepository;
import org.freeshr.infrastructure.persistence.FacilityRepository;
import org.freeshr.launch.WebMvcConfig;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cassandra.core.CqlOperations;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Resource;
import java.util.concurrent.ExecutionException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(initializers = SHREnvironmentMock.class, classes = {WebMvcConfig.class, SHRConfig.class})
@WebAppConfiguration
public abstract class APIIntegrationTestBase {
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9997);

    @Resource
    private WebApplicationContext webApplicationContext;

    @Autowired
    private EncounterRepository encounterRepository;

    @Autowired
    protected SHRProperties shrProperties;

    @Autowired
    private FacilityRepository facilityRepository;

    protected MockMvc mockMvc;

    @Autowired
    @Qualifier("SHRCassandraTemplate")
    private CqlOperations cqlTemplate;

    @Before
    public void setUpBase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @After
    public void tearDownBase() {
        cqlTemplate.execute("truncate encounter");
        cqlTemplate.execute("truncate patient");
        cqlTemplate.execute("truncate facilities");
        cqlTemplate.execute("truncate enc_by_patient");
        cqlTemplate.execute("truncate enc_by_catchment");
    }

    public void createEncounter(EncounterBundle encounter, Patient patient) throws ExecutionException,
            InterruptedException {
        encounterRepository.save(encounter, patient).toBlocking().first();
    }

    public void createFacility(Facility facility) {
        facilityRepository.save(facility).toBlocking().first();
    }

    public BaseMatcher<EncounterSearchResponse> hasEncountersOfSize(final int expectedSize) {
        return new BaseMatcher<EncounterSearchResponse>() {

            @Override
            public boolean matches(Object item) {
                return ((EncounterSearchResponse) item).getEntries().size() == expectedSize;
            }

            @Override
            public void describeTo(Description description) {
                description.appendValue(expectedSize);

            }
        };
    }

    public String generateHealthId() {
        return java.util.UUID.randomUUID().toString();
    }

}
