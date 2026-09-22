package com.mmarushika.api_compatibility.sample;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@RestController

// @RequestMapping("/sample")
public class SampleController {
    @GetMapping("/sample1")
    ResponseEntity<String> sample1(
            @RequestParam("yearOfBirth") int yearOfBirth) {
        boolean error = false;
        if (error) {
            return new ResponseEntity<>(
                    "Year of birth cannot be in the future",
                    HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(
                "Your age is " + yearOfBirth,
                HttpStatus.OK);
    };
    @GetMapping("/sample2")
    ResponseEntity<String> sample2(
            @RequestParam("yearOfBirth") int yearOfBirth) {
        boolean error = true;
        if (error) {
            return new ResponseEntity<>(
                    "Year of birth cannot be in the future",
                    HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(
                "Your age is " + yearOfBirth,
                HttpStatus.OK);
    }

}
