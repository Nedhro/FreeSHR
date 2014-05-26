package org.freeshr.shr.encounter.service;

import org.freeshr.shr.concurrent.PreResolvedListenableFuture;
import org.freeshr.shr.encounter.model.Encounter;
import org.freeshr.shr.encounter.repository.AllEncounters;
import org.freeshr.shr.patient.service.PatientRegistry;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.ExecutionException;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class EncounterServiceTest {

    @Mock
    private AllEncounters allEncounters;
    @Mock
    private PatientRegistry patientRegistry;
    private EncounterService encounterService;

    @Before
    public void setup() {
        initMocks(this);
        encounterService = new EncounterService(allEncounters, patientRegistry);
    }

    /**
     * This test ensures that the result of patient validity is not queried eagerly which would result in a blocking call.
     *
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Test
    public void shouldNotSaveEncounterUnlessResultIsQueried() throws ExecutionException, InterruptedException {
        String healthId = "healthId";
        Encounter encounter = new Encounter();
        encounter.setHealthId(healthId);

        when(patientRegistry.isValid(healthId)).thenReturn(new PreResolvedListenableFuture<Boolean>(Boolean.TRUE));
        encounterService.ensureCreated(encounter);
        verify(allEncounters, never()).save(encounter);
    }

    @Test
    public void shouldSaveEncounterAsSoonAsResultIsQueried() throws ExecutionException, InterruptedException {
        String healthId = "healthId";
        Encounter encounter = new Encounter();
        encounter.setHealthId(healthId);

        when(patientRegistry.isValid(healthId)).thenReturn(new PreResolvedListenableFuture<Boolean>(Boolean.TRUE));
        assertTrue(encounterService.ensureCreated(encounter).get());
        verify(allEncounters).save(encounter);
    }

    @Test
    public void shouldNotSaveEncounterWhenHealthIdIsNotValid() throws ExecutionException, InterruptedException {
        String healthId = "healthId";
        Encounter encounter = new Encounter();
        encounter.setHealthId(healthId);

        when(patientRegistry.isValid(healthId)).thenReturn(new PreResolvedListenableFuture<Boolean>(Boolean.FALSE));
        assertFalse(encounterService.ensureCreated(encounter).get());
        verify(allEncounters, never()).save(encounter);
    }
}