package com.example.patient_system.controller;

import com.example.patient_system.model.Appointment;
import com.example.patient_system.model.Doctor;
import com.example.patient_system.model.Medication;
import com.example.patient_system.model.Patient;
import com.example.patient_system.service.AppointmentService;
import com.example.patient_system.service.DoctorService;
import com.example.patient_system.service.MedicationService;
import com.example.patient_system.service.PatientService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Controller
public class WebController {

    @Autowired
    private PatientService patientService;

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private MedicationService medicationService;

    private static final Logger logger = LoggerFactory.getLogger(WebController.class);

    @GetMapping("/")
    public String home(Model model,
                       @RequestParam(value = "patientId", required = false) Long patientId,
                       RedirectAttributes redirectAttributes) {
        logger.info("Accessing homepage, patientId: {}", patientId);
        if (patientId != null) {
            Patient patient = patientService.getPatientById(patientId);
            model.addAttribute("patientName", patient.getName() != null ? patient.getName() : "Unknown");
            model.addAttribute("patientId", patient.getId());

            List<Appointment> appointments = appointmentService.getAppointmentsByPatient(patient);
            model.addAttribute("appointments", appointments != null ? appointments : Collections.emptyList());
            model.addAttribute("appointmentCount", appointments != null ? appointments.size() : 0);

            List<Medication> medications = medicationService.getMedicationsByPatient(patient.getId());
            model.addAttribute("medications", medications != null ? medications : Collections.emptyList());
            model.addAttribute("medicationCount", medications != null ? medications.size() : 0);
        } else {
            logger.info("No patientId provided, displaying default home view");
            model.addAttribute("message", "Welcome to the Patient Management System. Please log in or register to manage your health records.");
            model.addAttribute("loginUrl", "/login");
            model.addAttribute("patientName", null);
            model.addAttribute("appointments", Collections.emptyList());
            model.addAttribute("appointmentCount", 0);
            model.addAttribute("medications", Collections.emptyList());
            model.addAttribute("medicationCount", 0);
        }

        if (redirectAttributes != null) {
            boolean hasError = redirectAttributes.getFlashAttributes().containsKey("error") &&
                    redirectAttributes.getFlashAttributes().get("error") != null;
            model.addAttribute("error", hasError);
            if (redirectAttributes.getFlashAttributes().containsKey("errorMessage")) {
                model.addAttribute("errorMessage", redirectAttributes.getFlashAttributes().get("errorMessage"));
            }
            if (redirectAttributes.getFlashAttributes().containsKey("success")) {
                model.addAttribute("success", redirectAttributes.getFlashAttributes().get("success"));
            }
        }
        return "index";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        logger.info("Displaying registration form");
        model.addAttribute("patient", new Patient());
        return "register";
    }

    @PostMapping("/register")
    public String processRegistration(@Valid @ModelAttribute("patient") Patient patient,
                                      BindingResult result,
                                      Model model,
                                      RedirectAttributes redirectAttributes) {
        logger.info("Processing registration for patient: {}", patient.getEmail());
        if (result.hasErrors()) {
            logger.warn("Validation errors found in registration form");
            return "register";
        }
        Patient savedPatient = patientService.registerPatient(patient);
        logger.info("Patient registered successfully: {}", savedPatient.getEmail());
        redirectAttributes.addFlashAttribute("success", "Registration successful!");
        return "redirect:/?patientId=" + savedPatient.getId();
    }

    @GetMapping("/login")
    public String showLoginForm(Model model, @RequestParam(value = "error", required = false) String error) {
        logger.info("Displaying login form, error parameter: {}", error);
        if (error != null) {
            model.addAttribute("error", true);
            model.addAttribute("errorMessage", "Invalid email or password. Please try again.");
        }
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(@RequestParam("username") String email,
                               @RequestParam("password") String password,
                               RedirectAttributes redirectAttributes) {
        logger.info("Processing login attempt for: {}", email);
        Patient patient = patientService.findByEmail(email);
        if (patient != null && password.equals(patient.getPassword())) {
            logger.info("Login successful for: {}", email);
            redirectAttributes.addFlashAttribute("success", "Welcome back, " + patient.getName() + "!");
            return "redirect:/?patientId=" + patient.getId();
        }
        logger.warn("Login failed for: {}", email);
        redirectAttributes.addFlashAttribute("error", true);
        redirectAttributes.addFlashAttribute("errorMessage", "Invalid email or password. Please try again.");
        return "redirect:/login";
    }

    @GetMapping("/appointments")
    public String showAppointments(Model model,
                                   @RequestParam(value = "patientId", required = false) Long patientId,
                                   RedirectAttributes redirectAttributes) {
        logger.info("Accessing appointments page for patientId: {}", patientId);
        if (patientId == null) {
            logger.warn("No patientId provided to /appointments");
            return "redirect:/login";
        }
        Patient patient = patientService.getPatientById(patientId);
        List<Doctor> doctors = doctorService.getAllDoctors();
        List<Appointment> appointments = appointmentService.getAppointmentsByPatient(patient);

        model.addAttribute("doctors", doctors != null ? doctors : Collections.emptyList());
        model.addAttribute("appointment", new Appointment());
        model.addAttribute("patientId", patient.getId());
        model.addAttribute("appointments", appointments != null ? appointments : Collections.emptyList());

        if (redirectAttributes != null) {
            if (redirectAttributes.getFlashAttributes().containsKey("success")) {
                model.addAttribute("success", redirectAttributes.getFlashAttributes().get("success"));
            }
            if (redirectAttributes.getFlashAttributes().containsKey("error")) {
                model.addAttribute("error", true);
                model.addAttribute("errorMessage", redirectAttributes.getFlashAttributes().get("error"));
            }
        }
        return "appointments";
    }

    @PostMapping("/appointments/book")
    public String bookAppointment(@RequestParam Long patientId,
                                  @RequestParam Long doctorId,
                                  @RequestParam String appointmentTime,
                                  @RequestParam(required = false) String notes,
                                  RedirectAttributes redirectAttributes) {
        logger.info("Received booking request - patientId: {}, doctorId: {}, appointmentTime: {}, notes: {}",
                patientId, doctorId, appointmentTime, notes);

        Patient patient = patientService.getPatientById(patientId);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        LocalDateTime time = LocalDateTime.parse(appointmentTime, formatter);

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctorService.getDoctorById(doctorId));
        appointment.setAppointmentTime(time);
        appointment.setStatus("SCHEDULED");
        appointment.setNotes(notes != null ? notes : "");
        appointmentService.bookAppointment(appointment);

        redirectAttributes.addFlashAttribute("success", "Appointment booked successfully!");
        return "redirect:/appointments?patientId=" + patientId;
    }

    @GetMapping("/medications")
    public String showMedications(Model model,
                                  @RequestParam(value = "patientId", required = false) Long patientId) {
        logger.info("Accessing medications page for patientId: {}", patientId);
        if (patientId == null) {
            return "redirect:/login";
        }
        Patient patient = patientService.getPatientById(patientId);
        List<Medication> medications = medicationService.getMedicationsByPatient(patient.getId());

        model.addAttribute("medications", medications != null ? medications : Collections.emptyList());
        model.addAttribute("medication", new Medication());
        model.addAttribute("patientId", patient.getId());
        return "medications";
    }

    @PostMapping("/medications/add")
    public String addMedication(@RequestParam Long patientId,
                                @ModelAttribute Medication medication,
                                RedirectAttributes redirectAttributes) {
        logger.info("Adding medication for patientId: {}, medication: {}", patientId, medication.getName());
        Patient patient = patientService.getPatientById(patientId);
        medicationService.addMedication(patient, medication);
        redirectAttributes.addFlashAttribute("success", "Medication added successfully!");
        return "redirect:/medications?patientId=" + patientId;
    }

    @PostMapping("/medications/delete/{id}")
    public String deleteMedication(@PathVariable Long id,
                                   @RequestParam Long patientId,
                                   RedirectAttributes redirectAttributes) {
        logger.info("Deleting medication id: {} for patientId: {}", id, patientId);
        medicationService.deleteMedication(id);
        redirectAttributes.addFlashAttribute("success", "Medication deleted successfully!");
        return "redirect:/medications?patientId=" + patientId;
    }

    @GetMapping("/profile")
    public String showProfile(Model model,
                              @RequestParam(value = "patientId", required = false) Long patientId) {
        logger.info("Accessing profile page for patientId: {}", patientId);
        if (patientId == null) {
            return "redirect:/login";
        }
        Patient patient = patientService.getPatientById(patientId);
        model.addAttribute("patient", patient);
        return "profile";
    }

    @GetMapping("/logout")
    public String logoutPage() {
        logger.info("Logout requested");
        return "redirect:/login?logout=true";
    }
}
