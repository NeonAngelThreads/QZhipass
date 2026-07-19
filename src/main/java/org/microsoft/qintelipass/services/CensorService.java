package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.dtos.CensorRecordDTO;
import org.microsoft.qintelipass.entity.CensorKeyword;
import org.microsoft.qintelipass.entity.CensorRecord;
import org.microsoft.qintelipass.repository.CensorKeywordRepository;
import org.microsoft.qintelipass.repository.CensorRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CensorService {

    private final CensorKeywordRepository censorKeywordRepository;
    private final CensorRecordRepository censorRecordRepository;
    private final VectorCensorService vectorCensorService;

    public CensorService(CensorKeywordRepository censorKeywordRepository,
                        CensorRecordRepository censorRecordRepository,
                        VectorCensorService vectorCensorService) {
        this.censorKeywordRepository = censorKeywordRepository;
        this.censorRecordRepository = censorRecordRepository;
        this.vectorCensorService = vectorCensorService;
    }

    @Transactional(readOnly = true)
    public List<CensorKeyword> listKeywords() {
        return censorKeywordRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public CensorKeyword addKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("Keyword must not be blank.");
        }

        return censorKeywordRepository.save(new CensorKeyword(keyword.trim()));
    }

    @Transactional
    public CensorKeyword setKeywordEnabled(Long id, boolean enabled) {
        CensorKeyword keyword = censorKeywordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Censor keyword does not exist."));

        keyword.setEnabled(enabled);
        return censorKeywordRepository.save(keyword);
    }

    @Transactional(readOnly = true)
    public List<CensorRecord> listRecords() {
        return censorRecordRepository.findAllByOrderByCreatedAtDesc();
    }

    
    @Transactional
    public void checkAndRecord(Long userId,
                               String username,
                               String phone,
                               String department,
                               String modelName,
                               String inputContent,
                               String outputContent) {
        String fullContent = "";

        if (inputContent != null) {
            fullContent += inputContent;
        }

        if (outputContent != null) {
            fullContent += "\n" + outputContent;
        }

        List<String> exactHits = findExactHitKeywords(fullContent);
        List<String> vectorHits = vectorCensorService.findSimilarSensitiveWords(fullContent);

        Set<String> allHits = new LinkedHashSet<>();
        allHits.addAll(exactHits);
        allHits.addAll(vectorHits);

        if (allHits.isEmpty()) {
            return;
        }

        CensorRecord record = new CensorRecord(
                userId,
                username,
                phone,
                department,
                modelName,
                String.join(",", allHits)
        );

        censorRecordRepository.save(record);

        checkMonthlyHitCountAndNotifyAdmin(userId, record);
    }

    private void checkMonthlyHitCountAndNotifyAdmin(Long userId, CensorRecord record) {
        YearMonth currentMonth = YearMonth.now();

        LocalDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime startOfNextMonth = currentMonth.plusMonths(1).atDay(1).atStartOfDay();

        long monthlyHitCount = censorRecordRepository.countByUserIdAndCreatedAtBetween(
                userId,
                startOfMonth,
                startOfNextMonth
        );

        if (monthlyHitCount == 3) {
            sendSecurityLogToAdmin(record);
        }
    }

    private void sendSecurityLogToAdmin(CensorRecord record) {
        System.out.println("Sensitive word warning: user hit sensitive words 3 times this month.");
        System.out.println("Username: " + record.getUsername());
        System.out.println("Phone: " + record.getPhone());
        System.out.println("Department: " + record.getDepartment());
        System.out.println("Model: " + record.getModelName());
        System.out.println("Hit keywords: " + record.getHitKeywords());
    }


    @Transactional(readOnly = true)
    public Page<CensorRecordDTO> listAllRecords(int page, int size) {
        return censorRecordRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(CensorRecordDTO::from);
    }

    @Transactional(readOnly = true)
    public Page<CensorRecordDTO> searchRecords(String query, int page, int size) {
        return censorRecordRepository
                .findByUsernameContainingOrHitKeywordsContainingAllIgnoreCaseOrderByCreatedAtDesc(
                        query, query, PageRequest.of(page, size))
                .map(CensorRecordDTO::from);
    }

    @Transactional(readOnly = true)
    public Page<CensorRecordDTO> listRecordsByUser(Long userId, int page, int size) {
        return censorRecordRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(CensorRecordDTO::from);
    }

    private List<String> findExactHitKeywords(String content) {
        List<String> hits = new ArrayList<>();

        if (content == null || content.isBlank()) {
            return hits;
        }

        List<CensorKeyword> keywords = censorKeywordRepository.findByEnabledTrue();

        for (CensorKeyword keyword : keywords) {
            String word = keyword.getKeyword();

            if (word != null && !word.isBlank() && content.contains(word)) {
                hits.add(word);
            }
        }

        return hits;
    }
}