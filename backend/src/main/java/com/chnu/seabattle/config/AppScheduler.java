package com.chnu.seabattle.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Log4j2
public class AppScheduler {

    // Reserved for upcoming background tasks (e.g. cleaning up unstarted or abandoned matches)
}
