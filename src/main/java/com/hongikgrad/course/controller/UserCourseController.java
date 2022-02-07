package com.hongikgrad.course.controller;

import com.hongikgrad.course.application.CourseService;
import com.hongikgrad.course.dto.UserTakenCourseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserCourseController {

    private final CourseService courseService;

    /* 유저가 들은 수업들을 응답 */
    @GetMapping("/users/courses")
    public ResponseEntity getUserCourses(HttpServletRequest request) {
        try {
            return new ResponseEntity<>(
                    courseService.getUserTakenCourses(request),
                    HttpStatus.OK
            );
        } catch (NullPointerException e) {
            return new ResponseEntity<>("유저의 정보를 알 수 없습니다.",
                    HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /* 유저가 들은 수업들 크롤링 한 뒤 데이터베이스에 저장 */
    @PostMapping("/users/courses")
    public ResponseEntity<String> saveUserCourses(HttpServletRequest request) {
        try {
            courseService.saveUserTakenCourses(request);
            return new ResponseEntity<>("저장 성공", HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch(NullPointerException e) {
            return new ResponseEntity<>("크롤링 실패", HttpStatus.BAD_REQUEST);
        }
    }
}
