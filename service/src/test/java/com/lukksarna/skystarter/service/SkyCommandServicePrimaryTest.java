package com.lukksarna.skystarter.service;

import com.lukksarna.skystarter.domain.command.CreateSkyCommand;
import com.lukksarna.skystarter.domain.command.DeleteSkyCommand;
import com.lukksarna.skystarter.domain.command.UpdateSkyCommand;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
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
class SkyCommandServicePrimaryTest {

    @Mock
    private CommandGateway commandGateway;

    @Test
    void createSky_generatesIdAndDispatchesCommand() {
        when(commandGateway.send(any(CreateSkyCommand.class), eq(Object.class)))
                .thenReturn(CompletableFuture.completedFuture(new Object()));

        SkyCommandServicePrimary service = new SkyCommandServicePrimary(commandGateway);
        UUID result = service.createSky("Andromeda").join();

        assertThat(result).isNotNull();

        ArgumentCaptor<CreateSkyCommand> captor = ArgumentCaptor.forClass(CreateSkyCommand.class);
        verify(commandGateway).send(captor.capture(), eq(Object.class));
        assertThat(captor.getValue().getSkyId()).isEqualTo(result);
        assertThat(captor.getValue().getName()).isEqualTo("Andromeda");
    }

    @Test
    void updateSky_dispatchesUpdateCommand() {
        UUID skyId = UUID.randomUUID();
        when(commandGateway.send(any(UpdateSkyCommand.class), eq(Object.class)))
                .thenReturn(CompletableFuture.completedFuture(new Object()));

        new SkyCommandServicePrimary(commandGateway).updateSky(skyId, "Renamed").join();

        ArgumentCaptor<UpdateSkyCommand> captor = ArgumentCaptor.forClass(UpdateSkyCommand.class);
        verify(commandGateway).send(captor.capture(), eq(Object.class));
        assertThat(captor.getValue().getSkyId()).isEqualTo(skyId);
        assertThat(captor.getValue().getName()).isEqualTo("Renamed");
    }

    @Test
    void deleteSky_dispatchesDeleteCommand() {
        UUID skyId = UUID.randomUUID();
        when(commandGateway.send(any(DeleteSkyCommand.class), eq(Object.class)))
                .thenReturn(CompletableFuture.completedFuture(new Object()));

        new SkyCommandServicePrimary(commandGateway).deleteSky(skyId).join();

        ArgumentCaptor<DeleteSkyCommand> captor = ArgumentCaptor.forClass(DeleteSkyCommand.class);
        verify(commandGateway).send(captor.capture(), eq(Object.class));
        assertThat(captor.getValue().getSkyId()).isEqualTo(skyId);
    }
}
