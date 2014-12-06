package org.freeshr.infrastructure.persistence;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import org.freeshr.domain.model.patient.Address;
import org.freeshr.domain.model.patient.Patient;
import org.freeshr.utils.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cassandra.core.CqlOperations;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.functions.Func1;

import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.commons.lang.StringUtils.join;
import static org.freeshr.infrastructure.persistence.RxMaps.completeResponds;
import static org.freeshr.infrastructure.persistence.RxMaps.respondOnNext;
import static org.freeshr.utils.CollectionUtils.map;

@Component
public class PatientRepository {
    private static final Logger logger = LoggerFactory.getLogger(PatientRepository.class);
    private CqlOperations cqlOperations;

    @Autowired
    public PatientRepository(@Qualifier("SHRCassandraTemplate") CqlOperations cqlOperations) {
        this.cqlOperations = cqlOperations;
    }

    public Observable<Patient> find(String healthId) {
        Observable<ResultSet> observable = Observable.from(
                cqlOperations.queryAsynchronously("SELECT " +
                        " health_id, gender, division_id, district_id, upazila_id, city_corporation_id, " +
                        "union_urban_ward_id, address_line" +
                        " FROM patient WHERE health_id='" + healthId + "';"));
        return observable.map(new Func1<ResultSet, Patient>() {
            @Override
            public Patient call(ResultSet rows) {
                return readPatient(rows);
            }
        });
    }

    private Patient readPatient(ResultSet resultSet) {
        Row result = resultSet.one();
        if (null != result) {
            Patient patient = new Patient();
            Address address = new Address();
            patient.setHealthId(result.getString("health_id"));
            patient.setGender(result.getString("gender"));
            address.setDivision(result.getString("division_id"));
            address.setDistrict(result.getString("district_id"));
            address.setUpazila(result.getString("upazila_id"));
            address.setCityCorporation(result.getString("city_corporation_id"));
            address.setWard(result.getString("union_urban_ward_id"));
            address.setLine(result.getString("address_line"));
            patient.setAddress(address);
            return patient;
        } else {
            return null;
        }
    }

    public Observable<Boolean> save(Patient patient) {
        Observable<ResultSet> saveObservable = Observable.from(cqlOperations.executeAsynchronously(toCQL(patient)));
        return saveObservable.flatMap(respondOnNext(true), RxMaps.<Boolean>logAndForwardError(logger),
                completeResponds(true));
    }

    private String toCQL(Patient patient) {
        Address address = patient.getAddress();
        String query = query(asList(patient.getHealthId(),
                patient.getGender(), address.getLine(), address.getDistrict(),
                address.getDivision(), address.getWard(), address.getUpazila(), address.getCityCorporation()));
        return "INSERT into patient (health_id, gender, address_line, district_id, division_id, union_urban_ward_id, " +
                "upazila_id, city_corporation_id) values  (" + query + ")";
    }


    private String query(List<String> values) {
        return join(map(values, new CollectionUtils.Fn<String, String>() {
            public String call(String input) {
                return "'" + input + "'";
            }
        }), ",");
    }
}
