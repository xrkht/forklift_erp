package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.entity.DataImportJob;
import com.example.forklift_erp.repository.DataImportJobRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataImportJobStatusServiceTests {

    @Test
    void markFailedPersistsFailureSnapshot() {
        DataImportJobRepository repository = mock(DataImportJobRepository.class);
        DataImportJob job = new DataImportJob();
        job.setId(7L);
        job.setStatus("IMPORTING");
        when(repository.findById(7L)).thenReturn(Optional.of(job));
        when(repository.save(any(DataImportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DataImportJob saved = new DataImportJobStatusService(repository)
                .markFailed(7L, "Workbook row failed", "tester");

        assertThat(saved.getStatus()).isEqualTo("FAILED");
        assertThat(saved.getSummary()).isEqualTo("Workbook row failed");
        assertThat(saved.getImportedBy()).isEqualTo("tester");
        assertThat(saved.getFinishedAt()).isNotNull();
    }
}
