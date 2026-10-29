package com.personalrecords.api.records;

import java.util.List;

import com.personalrecords.api.user.User;
import com.personalrecords.api.user.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentRecordService {

    private final StudentRecordRepository recordRepository;
    private final UserRepository userRepository;

    public StudentRecordService(StudentRecordRepository recordRepository, UserRepository userRepository) {
        this.recordRepository = recordRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public StudentRecordResponse create(String authenticatedEmail, StudentRecordRequest request) {
        User user = findAuthenticatedUser(authenticatedEmail);
        StudentRecord record = new StudentRecord(
                request.title(),
                request.description(),
                request.category(),
                request.institution(),
                request.startDate(),
                request.endDate(),
                request.status(),
                user);
        return StudentRecordResponse.from(recordRepository.saveAndFlush(record));
    }

    @Transactional(readOnly = true)
    public List<StudentRecordResponse> findAll(String authenticatedEmail) {
        User user = findAuthenticatedUser(authenticatedEmail);
        return recordRepository.findAllByUser_IdOrderByCreatedAtDescIdDesc(user.getId())
                .stream()
                .map(StudentRecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentRecordResponse findById(String authenticatedEmail, Long id) {
        User user = findAuthenticatedUser(authenticatedEmail);
        return StudentRecordResponse.from(findOwnedRecord(id, user.getId()));
    }

    @Transactional
    public StudentRecordResponse update(String authenticatedEmail, Long id, StudentRecordRequest request) {
        User user = findAuthenticatedUser(authenticatedEmail);
        StudentRecord record = findOwnedRecord(id, user.getId());
        record.update(
                request.title(),
                request.description(),
                request.category(),
                request.institution(),
                request.startDate(),
                request.endDate(),
                request.status());
        return StudentRecordResponse.from(recordRepository.saveAndFlush(record));
    }

    @Transactional
    public void delete(String authenticatedEmail, Long id) {
        User user = findAuthenticatedUser(authenticatedEmail);
        recordRepository.delete(findOwnedRecord(id, user.getId()));
    }

    private User findAuthenticatedUser(String authenticatedEmail) {
        return userRepository.findByNormalizedEmail(authenticatedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user was not found"));
    }

    private StudentRecord findOwnedRecord(Long id, Long userId) {
        return recordRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(StudentRecordNotFoundException::new);
    }
}
