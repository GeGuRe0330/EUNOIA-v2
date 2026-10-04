package com.eunoia.journal.infrastructure;

import com.eunoia.journal.domain.EmotionEntry;
import com.eunoia.journal.domain.EntryDateCount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface EmotionEntryJpaRepository extends JpaRepository<EmotionEntry, Long> {
    List<EmotionEntry> findByIdInAndMemberId(List<Long> entryIds, Long memberId);

    boolean existsByIdAndMemberId(Long entryId, Long memberId);

    @Query("""
            select e from EmotionEntry e
            where e.memberId = :memberId
                and (:from is null or e.entryDate >= :from)
                and (:to is null or e.entryDate <= :to)
            order by e.entryDate desc, e.id desc
            """)
    Slice<EmotionEntry> findSliceByMemberIdAndPeriod(Long memberId, LocalDate from, LocalDate to, Pageable pageable);

    @Query("""
            select count(e) from EmotionEntry e
            where e.memberId = :memberId
                and (:from is null or e.entryDate >= :from)
                and (:to is null or e.entryDate <= :to)
            """)
    long countByMemberIdAndPeriod(Long memberId, LocalDate from, LocalDate to);

    @Query("""
            select new com.eunoia.journal.domain.EntryDateCount(e.entryDate, count(e))
            from EmotionEntry e
            where e.memberId = :memberId
                and e.entryDate between :from and :to
            group by e.entryDate
            order by e.entryDate
            """)
    List<EntryDateCount> countDailyByMemberIdAndPeriod(Long memberId, LocalDate from, LocalDate to);
}
