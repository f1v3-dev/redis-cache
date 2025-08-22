package com.f1v3.cache.controller;

import com.f1v3.cache.clients.test.TestSearchBookAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class TestController {

    @GetMapping("/api/test-count")
    public String testCount() {
        return TestSearchBookAdapter.REQUEST_COUNT.toString();
    }
}
