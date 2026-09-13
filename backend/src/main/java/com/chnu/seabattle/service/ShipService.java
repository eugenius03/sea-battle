package com.chnu.seabattle.service;

import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.repository.ShipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShipService extends AbstractBaseService<Ship, Long> {

    private final ShipRepository shipRepository;

    @Override
    protected ShipRepository getRepository() {
        return shipRepository;
    }
}
