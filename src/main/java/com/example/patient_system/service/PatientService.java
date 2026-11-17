package com.example.patient_system.service;

import com.example.patient_system.model.Patient;
import com.example.patient_system.repository.PatientRepository;
import org.springframework.stereotype.Service;

@Service
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public Patient registerPatient(Patient patient) {
        String email = patient.getEmail().toLowerCase().trim();
        if (patientRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        patient.setEmail(email);
        // Security/authentication layer removed: plain password stored
        patient.setPassword(patient.getPassword());
        return patientRepository.save(patient);
    }

    public Patient getPatientById(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
    }

    public Patient findByEmail(String email) {
        return patientRepository.findByEmailIgnoreCase(email.toLowerCase().trim());
    }
}
