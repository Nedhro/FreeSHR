package org.freeshr.validations;

import ca.uhn.fhir.model.dstu2.resource.Bundle;
import org.freeshr.application.fhir.EncounterBundle;
import org.freeshr.utils.FhirFeedUtil;

public class EncounterValidationContext {
    private EncounterBundle encounterBundle;
    private FhirFeedUtil fhirFeedUtil;
    private org.hl7.fhir.instance.model.Bundle feed;
    private Bundle bundle;

    public EncounterValidationContext(EncounterBundle encounterBundle,
                                      FhirFeedUtil fhirFeedUtil) {
        this.encounterBundle = encounterBundle;
        this.fhirFeedUtil = fhirFeedUtil;
    }

    public org.hl7.fhir.instance.model.Bundle getFeed() {
        //deserialize only once
        if (feed != null) return feed;
        feed = fhirFeedUtil.deserialize(encounterBundle.getContent());
        return feed;
    }

    public String getHealthId() {
        return this.encounterBundle.getHealthId();
    }


    public ValidationSubject<org.hl7.fhir.instance.model.Bundle> feedFragment() {
        return new ValidationSubject<org.hl7.fhir.instance.model.Bundle>() {
            @Override
            public org.hl7.fhir.instance.model.Bundle extract() {
                return getFeed();
            }
        };
    }

    public ValidationSubject<Bundle> bundleFragment() {
        return new ValidationSubject<Bundle>() {
            @Override
            public Bundle extract() {
                return getBundle();
            }
        };
    }

    public ValidationSubject<EncounterValidationContext> context() {
        return new ValidationSubject<EncounterValidationContext>() {
            @Override
            public EncounterValidationContext extract() {
                return EncounterValidationContext.this;
            }
        };
    }

    public ValidationSubject<String> sourceFragment() {
        return new ValidationSubject<String>() {
            @Override
            public String extract() {
                return encounterBundle.getContent();
            }
        };
    }

    public Bundle getBundle() {
        if (bundle != null) return bundle;
        bundle = fhirFeedUtil.parseBundle(encounterBundle.getContent(), "xml");
        return bundle;
    }
}
