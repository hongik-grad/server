package com.hongikgrad.major.repository;

import com.hongikgrad.course.dto.CourseDto;
import com.hongikgrad.major.dto.MajorDto;
import com.hongikgrad.major.entity.Major;

import java.util.List;

public interface MajorHierarchyRepositoryCustom {
	List<Major> findSlavesByMaster(MajorDto master);
	List<CourseDto> findAllMajorCoursesByMaster(Major master);
	List<CourseDto> findAllMajorCoursesByMaster(String majorCode);
	List<CourseDto> findAllMajorCoursesByMaster(Long majorId);
}
