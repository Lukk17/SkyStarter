package com.lukksarna.skystarter.service;

import com.lukksarna.skystarter.domain.model.Sky;
import com.lukksarna.skystarter.domain.model.SkyStatus;
import com.lukksarna.skystarter.domain.query.FindSkyByIdQuery;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkyQueryServicePrimaryTest {

    @Mock
    private QueryGateway queryGateway;

    @Test
    void findById_returnsSkyFromQueryGateway() {
        UUID skyId = UUID.randomUUID();
        Sky expected = new Sky(skyId, "Orion", SkyStatus.CREATED);
        when(queryGateway.query(any(FindSkyByIdQuery.class), eq(Sky.class)))
                .thenReturn(CompletableFuture.completedFuture(expected));

        Sky actual = new SkyQueryServicePrimary(queryGateway).findById(skyId).join();

        assertThat(actual).isEqualTo(expected);

        ArgumentCaptor<FindSkyByIdQuery> captor = ArgumentCaptor.forClass(FindSkyByIdQuery.class);
        verify(queryGateway).query(captor.capture(), eq(Sky.class));
        assertThat(captor.getValue().getSkyId()).isEqualTo(skyId);
    }
}
