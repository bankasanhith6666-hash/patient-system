package com.example.patient_system.service;

import com.example.patient_system.model.Appointment;
import com.example.patient_system.model.Doctor;
import com.example.patient_system.model.Patient;
import com.example.patient_system.repository.AppointmentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentService.class);
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private MeterRegistry meterRegistry;

    public Appointment bookAppointment(Appointment appointment) {
        Patient patient = appointment.getPatient();
        Doctor doctor = doctorService.getDoctorById(appointment.getDoctor().getId());
        LocalDateTime appointmentTime = appointment.getAppointmentTime();

        if (patient == null || doctor == null || appointmentTime == null) {
            logger.error("Invalid appointment data: patient={}, doctor={}, appointmentTime={}",
                    patient, doctor, appointmentTime);
            throw new IllegalArgumentException("Patient, doctor, and appointment time must not be null");
        }

        List<Appointment> conflictingAppointments = appointmentRepository
                .findByDoctorIdAndAppointmentTimeBetween(
                        doctor.getId(),
                        appointmentTime.minusMinutes(30),
                        appointmentTime.plusMinutes(30)
                );
        if (!conflictingAppointments.isEmpty()) {
            logger.warn("Appointment slot unavailable for doctorId={} at {}",
                    doctor.getId(), appointmentTime);
            throw new IllegalArgumentException("Appointment slot is not available");
        }

        appointment.setDoctor(doctor);
        if (appointment.getStatus() == null) {
            appointment.setStatus("SCHEDULED");
        }
        logger.info("Booking appointment for patientId={} with doctorId={} at {}",
                patient.getId(), doctor.getId(), appointmentTime);

        Appointment savedAppointment = appointmentRepository.save(appointment);

        // The hour tag uses the server's local clock and counts when the booking is created.
        String createdHour = LocalDateTime.now().format(HOUR_FORMAT);
        Counter.builder("appointments.created")
                .description("Number of appointments created, grouped by creation hour")
                .tag("hour", createdHour)
                .register(meterRegistry)
                .increment();

        return savedAppointment;
    }

    public List<Appointment> getAppointmentsByPatient(Patient patient) {
        if (patient == null) {
            logger.warn("Patient is null in getAppointmentsByPatient");
            return Collections.emptyList();
        }
        List<Appointment> appointments = appointmentRepository.findByPatientId(patient.getId());
        if (appointments == null) {
            logger.info("No appointments found for patientId={}", patient.getId());
            return Collections.emptyList();
        }
        List<Appointment> validAppointments = appointments.stream()
                .filter(a -> {
                    boolean isValid = a != null && a.getAppointmentTime() != null && a.getDoctor() != null && a.getDoctor().getName() != null;
                    if (!isValid) {
                        logger.warn("Invalid appointment found: id={}, appointmentTime={}, doctor={}, doctorName={}",
                                a != null ? a.getId() : null,
                                a != null ? a.getAppointmentTime() : null,
                                a != null ? a.getDoctor() : null,
                                a != null && a.getDoctor() != null ? a.getDoctor().getName() : null);
                    }
                    return isValid;
                })
                .collect(Collectors.toList());
        logger.info("Retrieved {} valid appointments for patientId={}",
                validAppointments.size(), patient.getId());
        return validAppointments;
    }
}
