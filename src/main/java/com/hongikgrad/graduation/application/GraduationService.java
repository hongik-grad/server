package com.hongikgrad.graduation.application;

import com.hongikgrad.common.application.CookieService;
import com.hongikgrad.course.dto.CourseDto;
import com.hongikgrad.major.entity.Major;
import com.hongikgrad.course.repository.CourseRepository;
import com.hongikgrad.major.repository.MajorCourseRepository;
import com.hongikgrad.major.repository.MajorHierarchyRepository;
import com.hongikgrad.major.repository.MajorRepository;
import com.hongikgrad.graduation.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GraduationService {

	private final CourseRepository courseRepository;
	private final MajorCourseRepository majorCourseRepository;
	private final MajorRepository majorRepository;
	private final MajorHierarchyRepository majorHierarchyRepository;

	private final CookieService cookieService;

	private List<String> writingCourseNumberList;
	private String englishCourseNumber;
	private List<String> specializedElectiveNumber;

	@PostConstruct
	public void init() {
		writingCourseNumberList = new ArrayList<>(List.of(
				"001011",
				"001012",
				"001013",
				"001014",
				"001015",
				"001020",
				"001021",
				"001022"
		));
		englishCourseNumber = "001009";
		specializedElectiveNumber = new ArrayList<>(List.of("008751", "008752"));
	}

	public List<RequirementDto> getGraduationRequirementResult(GraduationRequestDto request) {
		List<RequirementDto> result = new ArrayList<>();
		StudentDto student = getStudent(request);

		// 전공기초영어
		checkMajorBasicEnglish(student, result);

		// 영어, 글쓰기
		checkBasicElective(student, result);

		// 드래곤볼
		checkDragonball(student, result);

		// MSC
		if (isMajorInEngineering(student)) {
			checkMSC(student, result);
		}

		// 특성화교양
		if (isSpecializedElectiveRequired(student)) {
			checkSpecializedElective(student, result);
		}

		checkMajorCourse(student, result);

		// 필수전공
		checkRequiredMajor(student, result);

		// 전체학점
		checkTotalCredit(student, result);

		return result;
	}

	private boolean isMajorInEngineering(StudentDto student) {
		return student.getMajor().getCollege().equals("공과대학");
	}

	private boolean isSpecializedElectiveRequired(StudentDto student) {
		return student.getEnterYear() >= 19;
	}

	private void checkMajorBasicEnglish(StudentDto student, List<RequirementDto> result) {
		List<CourseDto> takenCourses = student.getTakenCourses();
		for (CourseDto course : takenCourses) {
			if (isMajorBasicEnglish(course)) {
				takeMajorBasicEnglish(course, result);
				return;
			}
		}
		notTakeMajorBasicEnglish(result);
	}

	private void checkBasicElective(StudentDto student, List<RequirementDto> result) {
		SubField writingCategory = new SubField("글쓰기", new ArrayList<>(), 0, false, "/courses?type=grad&keyword=writing");
		SubField englishCategory = new SubField("영어", new ArrayList<>(), 0, false, "/courses?type=grad&keyword=english");

		List<CourseDto> takenCourses = student.getTakenCourses();
		for (CourseDto course : takenCourses) {
			if (isWritingCourse(course)) {
				takeCourse(course, writingCategory);
			} else if (isEnglishCourse(course)) {
				takeCourse(course, englishCategory);
			}
		}

		List<SubField> subFields = combineSubFields(writingCategory, englishCategory);
		Integer totalCredit = getTotalCreditFromSubField(subFields);
		boolean isSatisfied = (writingCategory.getTotalCredit() > 1) && (englishCategory.getTotalCredit() > 1);

		RequirementDto requirement = new RequirementDto(
				"기초교양",
				totalCredit,
				"기초교양(6학점)",
				isSatisfied,
				subFields
		);
		result.add(requirement);
	}

	private void checkDragonball(StudentDto student, List<RequirementDto> result) {
		Map<String, SubField> subFieldMap = getDragonballSubFields();
		List<SubField> subFieldList = new ArrayList<>(subFieldMap.values());
		List<CourseDto> takenCourses = student.getTakenCourses();
		for (CourseDto course : takenCourses) {
			String abeek = course.getAbeek();
			if (isDragonball(abeek)) {
				SubField subField = subFieldMap.get(abeek);
				takeCourse(course, subField);
			}
		}

		int count = getDragonballCount(subFieldMap);
		int totalCredit = getTotalCreditFromSubField(subFieldList);

		String brifing = "‘예술과 디자인’, ‘제2외국어와 한문’ 영역을 반드시 포함하여 7개 영역 중 6개 영역을 선택하여 각 영역별 1과목 이상 이수하여야 함.";

		boolean isSatisfied = (subFieldMap.get("예술과디자인").getTotalCredit() > 1)
				&& (subFieldMap.get("제2외국어와한문").getTotalCredit() > 1)
				&& (count >= 4);

		RequirementDto requirement = new RequirementDto(
				"드래곤볼",
				totalCredit,
				brifing,
				isSatisfied,
				subFieldList
		);
		result.add(requirement);
	}

	private void checkMSC(StudentDto student, List<RequirementDto> result) {
		Map<String, SubField> subFieldMap = getMSCSubFields(student.getMajor().getCode(), student.getEnterYear());
		List<SubField> subFieldList = new ArrayList<>(subFieldMap.values());
		List<CourseDto> takenCourses = student.getTakenCourses();
		for (CourseDto course : takenCourses) {
			if (isAbeekCourse(course)) {
				String abeek = course.getAbeek();
				SubField subField = subFieldMap.get(abeek);
				takeCourse(course, subField);
			}
		}

		int totalCredit = getTotalCreditFromSubField(subFieldList);
		String briefing = getMSCBriefing(student);
		boolean isSatisfied = isSatisfiedMSC(student, subFieldMap);

		RequirementDto requirement = new RequirementDto(
				"MSC",
				totalCredit,
				briefing,
				isSatisfied,
				subFieldList
		);
		result.add(requirement);
	}

	private void checkSpecializedElective(StudentDto student, List<RequirementDto> result) {
		SubField subField = new SubField("특성화교양", new ArrayList<>(), 0, false, "/courses?type=grad&keyword=specializedelective");

		List<CourseDto> takenCourses = student.getTakenCourses();
		for (CourseDto course : takenCourses) {
			if (isSpecializedElective(course)) {
				takeCourse(course, subField);
			}
		}

		Integer totalCredit = getTotalCreditFromSubField(subField);
		boolean isSatisfied = subField.getTotalCredit() > 1;
		String briefing = "특성화교양(디자인씽킹, 창업과 실용법률) 중 한 과목을 반드시 이수하여야 함.";

		RequirementDto requirement = new RequirementDto(
				"특성화교양",
				totalCredit,
				briefing,
				isSatisfied,
				subField
		);
		result.add(requirement);
	}

	private void checkMajorCourse(StudentDto student, List<RequirementDto> result) {
		SubField subField = new SubField("전공", new ArrayList<>(), 0, false, "/courses?type=major&keyword=" + student.getMajor().getId());
		takeMajorCourse(student, subField);
		int totalCredit = getTotalCreditFromSubField(subField);
		String briefing = getMajorBriefing(student.getMajor());
		boolean isSatisfied = totalCredit >= 50;

		RequirementDto requirement = new RequirementDto(
				"전공",
				totalCredit,
				briefing,
				isSatisfied,
				subField
		);
		result.add(requirement);
	}

	private String getMajorBriefing(Major major) {
		String college = major.getCollege();
		switch(college) {
			case "미술대학":
				return "전공필수 모두 포함하여 전공 48학점 이상 이수\n" +
						"(전공 48학점 내에는 전공기초과목이 포함되지 않음.)";
			case "공과대학":
				return "전공(전공필수 모두 포함) 50학점 이상 이수";
			default:
				return "";
		}
	}

	private void checkRequiredMajor(StudentDto student, List<RequirementDto> result) {
		SubField subField = new SubField("전공필수", new ArrayList<>(), 0, false, "/courses?type=required&keyword=" + student.getMajor().getId());
		takeRequiredMajorCourse(student, subField);

		int totalCredit = getTotalCreditFromSubField(subField);
		boolean isSatisfied = checkRequireMajorSatisfaction(student);
		String briefing = "각 학과마다 지정된 전공필수 과목을 확인하세요!";

		RequirementDto requirement = new RequirementDto(
				"전공필수",
				totalCredit,
				briefing,
				isSatisfied,
				subField
		);
		result.add(requirement);
	}

	private void takeRequiredMajorCourse(StudentDto student, SubField subField) {
		List<CourseDto> takenCourses = student.getTakenCourses();
		List<CourseDto> requiredCourse = getRequiredCourse(student.getMajor());
		for (CourseDto course : takenCourses) {
			if (isRequiredMajor(course, requiredCourse)) {
				takeCourse(course, subField);
			}
		}
	}

	private void checkMajorCSOver20(StudentDto student, List<RequirementDto> result) {
		List<CourseDto> takenCourses = student.getTakenCourses();
	}

	private void checkMajorCredit(StudentDto student, List<RequirementDto> result) {
		List<CourseDto> takenCourses = student.getTakenCourses();
		Major studentMajor = student.getMajor();
		List<CourseDto> majorCourses = majorCourseRepository.findCourseDtosByMajor(studentMajor);

		SubField majorSubField = new SubField("", new ArrayList<>(), 0, false);

		for (CourseDto course : takenCourses) {
			if (majorCourses.contains(course)) {
//				totalCredit += course.getCredit();
				takeCourse(course, majorSubField);
			}
		}

		int totalCredit = getTotalCreditFromSubField(majorSubField);
		boolean isSatisfied = totalCredit >= 50;

		RequirementDto requirement = new RequirementDto(
				"전공 수강학점",
				totalCredit,
				"전공(전공필수 모두 포함하여 50학점)을 이수하여야 함.",
				isSatisfied,
				majorSubField
		);

		result.add(requirement);
	}

	private void checkTotalCredit(StudentDto student, List<RequirementDto> result) {
		List<CourseDto> takenCourses = student.getTakenCourses();

		int totalCredit = 0;
		for (CourseDto takenCourse : takenCourses) {
			totalCredit += takenCourse.getCredit();
		}

		boolean isSatisfied = totalCredit >= 132;

		RequirementDto requirement = new RequirementDto(
				"전체 수강학점",
				totalCredit,
				"총 132학점 이상(일반선택 포함) 이수하여야 함.",
				isSatisfied
		);

		result.add(requirement);
	}

	private void takeCourse(CourseDto course, SubField subField) {
		if(course == null || subField == null) return;
		List<CourseDto> courseList = subField.getCourseList();
		courseList.add(course);
		subField.setTotalCredit(subField.getTotalCredit() + course.getCredit());
	}

	private boolean isSatisfiedMSC(StudentDto student, Map<String, SubField> subFieldMap) {
		Major major = student.getMajor();
		String majorCode = major.getCode();
		int enterYear = student.getEnterYear();
		boolean isAbeek = student.isAbeek();
		List<CourseDto> takenCourses = student.getTakenCourses();

		SubField math = subFieldMap.get("MSC수학");
		SubField science = subFieldMap.get("MSC과학");
		SubField computer = subFieldMap.get("MSC전산");

		int mathCredit = math == null ? 0 : math.getTotalCredit();
		int scienceCredit = science == null ? 0 : science.getTotalCredit();
		int computerCredit = computer == null ? 0 : computer.getTotalCredit();

		if (isAbeek) {
			// 전체 학점 검사
			if (mathCredit < 9 || scienceCredit < 8 || computerCredit < 6
					|| mathCredit + scienceCredit + computerCredit < 30) {
				return false;
			}

			// 필수이수과목 검사
			if (majorCode.equals("EE")) {
				if (hasTakenPhysics2(takenCourses) && hasTakenChemistry1(takenCourses)
						&& (hasTakenPhysics2(takenCourses) || hasTakenChemistry2(takenCourses))) {
					return true;
				}
			} else {
				int takenCount = 0;
				if (hasTakenPhysics1(takenCourses)) takenCount += 1;
				if (hasTakenPhysics2(takenCourses)) takenCount += 1;
				if (hasTakenChemistry1(takenCourses)) takenCount += 1;
				if (hasTakenChemistry2(takenCourses)) takenCount += 1;
				if (takenCount >= 2) {
					return true;
				}
				return false;
			}
		} else {
			// abeek 비인증
			// 전체 학점 검사
			if (majorCode.equals("CS")) {
				if (mathCredit < 9 || scienceCredit < 9) return false;
			} else {
				if (mathCredit < 9 || scienceCredit < 9 || computerCredit < 6) return false;
			}

			// 필수이수과목 검사
			if (majorCode.equals("EE")) {
				if (hasTakenPhysics2(takenCourses) && hasTakenChemistry1(takenCourses)
						&& (hasTakenPhysics2(takenCourses) || hasTakenChemistry2(takenCourses))) {
					return true;
				}
				return false;
			} else if (enterYear >= 20 && majorCode.equals("CS")) {
				int takenCount = 0;
				if (hasTakenInformationSystem(takenCourses)) takenCount += 1;
				if (hasTakenOOP(takenCourses)) takenCount += 1;
				if (hasTakenCProgramming(takenCourses)) takenCount += 1;

				if (takenCount >= 2) {
					return true;
				}
				return false;

			} else if (enterYear >= 20 && majorCode.equals("IE")) {
				int takeCount = 0;
				if (hasTakenInformationSystem(takenCourses)) takeCount += 1;
				if (hasTakenWebProgramming(takenCourses)) takeCount += 1;
				if (hasTakenCProgramming(takenCourses)) takeCount += 1;

				if (takeCount >= 2) {
					return true;
				}
				return false;
			} else {
				if (hasTakenPhysics1(takenCourses) && hasTakenChemistry1(takenCourses)
						&& (hasTakenPhysics2(takenCourses) || hasTakenChemistry2(takenCourses))) {
					return true;
				}
				return false;
			}
		}
		return false;
	}

	private String getMSCBriefing(StudentDto student) {
		String majorCode = student.getMajor().getCode();
		int enterYear = student.getEnterYear();
		boolean isAbeek = student.isAbeek();

		if (isAbeek) {
			if (majorCode.equals("EE")) {
				return "분야별 최소이수학점(과학 8학점, 수학 9학점, 전산 6학점)을 포함하여 30학점 이상 이수하여야 함.\n" +
						"MSC 과학분야 중\n" +
						"대학물리(2), 대학물리실험(2), 대학화학(1), 대학화학실험(1)을 반드시 이수하여야 하고,\n" +
						"{대학물리(1),대학물리실험(1)} 와｛대학화학(2), 대학화학실험(2)｝둘 중 택일하여 이수하여야 함.\n";
			}
			return "분야별 최소이수학점(과학 8학점, 수학 9학점, 전산 6학점)을 포함하여 30학점 이상 이수하여야 함.\n" +
					"MSC 과학분야 중\n" +
					"{대학물리(1), 대학물리실험(1)}, {대학화학(1), 대학화학실험(1)}, {대학물리(2), 대학물리실험(2),}, {대학화학(2), 대학화학실험(2)}\n" +
					"4Set 중 2Set를 선택하여 이수하여야 함.\n";
		} else {
			if (majorCode.equals("EE")) {
				return "24학점 이상 이수하여야 함.\n" +
						"MSC 과학분야 중\n" +
						"대학물리(2), 대학물리실험(2), 대학화학(1), 대학화학실험(1)을 반드시 이수하여야 하고,\n" +
						"{대학물리(1),대학물리실험(1)} 와 {대학화학(2), 대학화학실험(2)} 둘 중 택일하여 이수하여야 함.\n";
			} else if (enterYear >= 20) {
				if (majorCode.equals("CS")) {
					return "MSC 과학분야 내 상기의 대학화학, 대학물리에 대한 별도 이수 요건 없이 MSC 수학분야 및 과학분야 내 과목 이수학점 합이 18학점 이상 되면 인정함.\n" +
							"<정보시스템개론, 객체지향프로그래밍, C-프로그래밍> 중 6학점을 이수해야 함.\n";
				} else if (majorCode.equals("IE")) {
					return "MSC 과학분야 내 상기의 대학화학, 대학물리에 대한 별도 이수 요건 없이 MSC 수학분야 및 과학분야 내 과목 이수학점 합이 18학점 이상 되면 인정함.\n" +
							"<정보시스템개론, 웹프로그래밍, C-프로그래밍> 중 6학점을 이수해야 함.\n";
				}
			} else if(majorCode.equals("CS")) {
				return "18학점 이상 이수하여야함\n" +
						"MSC 과학분야 중\n" +
						"대학물리(1), 대학물리실험(1), 대학화학(1), 대학화학실험(1)을 반드시 이수하여야 하고,\n" +
						"{대학물리(2), 대학물리실험(2)} 와 {대학화학(2), 대학화학실험(2)} 둘 중 택일하여 이수하여야 함.\n";

			} else {
				return "24학점 이상 이수하여야 함.\n" +
						"MSC 과학분야 중\n" +
						"대학물리(1), 대학물리실험(1), 대학화학(1), 대학화학실험(1)을 반드시 이수하여야 하고,\n" +
						"{대학물리(2), 대학물리실험(2)} 와 {대학화학(2), 대학화학실험(2)} 둘 중 택일하여 이수하여야 함.\n";
			}
		}

		return null;
	}

	private void takeMajorCourse(StudentDto student, SubField subField) {
		Major studentMajor = student.getMajor();
		int enterYear = student.getEnterYear();
		List<CourseDto> takenCourses = student.getTakenCourses();
		List<CourseDto> majorCourseList = getMajorCourseList(studentMajor, enterYear);
		takenCourses.forEach(courseDto -> {
			if (majorCourseList.contains(courseDto)) {
				takeCourse(courseDto, subField);
			}
		});
	}

	private void notTakeMajorBasicEnglish(List<RequirementDto> result) {
		RequirementDto requirement = RequirementDto.builder()
				.mainField("전공기초영어")
				.briefing("전공기초영어(Ⅰ/Ⅱ) 중 한 과목을 반드시 이수하여야 함.")
				.isSatisfied(false)
				.totalCredit(0)
				.build();
		result.add(requirement);
	}

	private void takeMajorBasicEnglish(CourseDto course, List<RequirementDto> result) {
		RequirementDto requirement = RequirementDto.builder()
				.mainField("전공기초영어")
				.briefing("전공기초영어(Ⅰ/Ⅱ) 중 한 과목을 반드시 이수하여야 함.")
				.isSatisfied(true)
				.totalCredit(course.getCredit())
				.subField(List.of(new SubField("전공기초영어", List.of(course), course.getCredit(), true)))
				.build();
		result.add(requirement);
	}

	private boolean isMajorBasicEnglish(CourseDto course) {
		String majorBasicEnglish1Number = "007114";
		String majorBasicEnglish2Number = "007115";
		if (course.getNumber().equals(majorBasicEnglish1Number)
				|| course.getNumber().equals(majorBasicEnglish2Number)) {
			return true;
		}
		return false;
	}

	private boolean isEnglishCourse(CourseDto course) {
		return course.getNumber().equals(englishCourseNumber);
	}

	private boolean isWritingCourse(CourseDto course) {
		String courseNumber = course.getNumber();
		for (String writingCourseNumber : writingCourseNumberList) {
			if (writingCourseNumber.equals(courseNumber)) {
				return true;
			}
		}
		return false;
	}

	private boolean isDragonball(String abeek) {
		return (abeek != null) && !(abeek.length() <= 3 || abeek.contains("MSC") || abeek.contains("교양"));
	}

	private Map<String, SubField> getMSCSubFields() {
		SubField math = new SubField("MSC수학", new ArrayList<>(), 0, false);
		SubField science = new SubField("MSC과학", new ArrayList<>(), 0, false);
		SubField computer = new SubField("MSC전산", new ArrayList<>(), 0, false);

		Map<String, SubField> subFields = new HashMap<>();
		subFields.put("MSC수학", math);
		subFields.put("MSC과학", science);
		subFields.put("MSC전산", computer);
		return subFields;
	}

	private Map<String, SubField> getMSCSubFields(String majorCode, int enterYear) {
		SubField math = new SubField("MSC수학", new ArrayList<>(), 0, false);
		SubField science = new SubField("MSC과학", new ArrayList<>(), 0, false);
		SubField computer = new SubField("MSC전산", new ArrayList<>(), 0, false);

		Map<String, SubField> subFields = new HashMap<>();
		subFields.put("MSC수학", math);
		subFields.put("MSC과학", science);
		subFields.put("MSC전산", computer);
		if(isMajorCSAndUnder19(majorCode, enterYear)) {
			subFields.remove("MSC전산");
		}
		return subFields;
	}

	private boolean checkRequireMajorSatisfaction(StudentDto student) {
		return false;
	}

	private boolean hasTakenPhysics1(List<CourseDto> courses) {
		return courses.contains(new CourseDto("012101", 3)) && courses.contains(new CourseDto("012103", 1));
	}

	private boolean hasTakenPhysics2(List<CourseDto> courses) {
		return courses.contains(new CourseDto("012104", 3)) && courses.contains(new CourseDto("012106", 1));
	}

	private boolean hasTakenChemistry1(List<CourseDto> courses) {
		return courses.contains(new CourseDto("012107", 3)) && courses.contains(new CourseDto("012109", 1));
	}

	private boolean hasTakenChemistry2(List<CourseDto> courses) {
		return courses.contains(new CourseDto("012110", 3)) && courses.contains(new CourseDto("012113", 1));
	}

	private StudentDto getStudent(GraduationRequestDto request) {
		return StudentDto.builder()
				.enterYear(request.getEnterYear())
				.major(getMajorById(request.getMajorId()))
				.takenCourses(request.getCourseList())
				.isAbeek(request.isAbeek())
				.build();
	}

	private boolean getAbeekWhether(HttpServletRequest request) {
		String abeek = request.getParameter("abeek");
		return abeek.equals("true");
	}

	private Major getMajorById(Long majorId) {
		return majorRepository.findMajorById(majorId);
	}

	private List<SubField> combineSubFields(SubField... subFields) {
		return Arrays.asList(subFields);
	}

	private Integer getTotalCreditFromSubField(List<SubField> subFields) {
		Integer totalCredit = 0;
		for (SubField subField : subFields) {
			totalCredit += subField.getTotalCredit();
		}
		return totalCredit;
	}

	private Integer getTotalCreditFromSubField(SubField subField) {
		return subField.getTotalCredit();
	}

	private int getDragonballCount(Map<String, SubField> subFields) {
		// 예술과디자인, 제2외국어와한문 영역 제외
		int count = 0;
		for (String key : subFields.keySet()) {
			SubField subField = subFields.get(key);
			String abeek = subField.getField();
			if (abeek.contains("예술") || abeek.contains("외국어")) continue;
			if (subField.getTotalCredit() > 1) count += 1;
		}
		return count;
	}

	private Map<String, SubField> getDragonballSubFields() {
		SubField history = new SubField("역사와문화", new ArrayList<>(), 0, false);
		SubField language = new SubField("언어와철학", new ArrayList<>(), 0, false);
		SubField society = new SubField("사회와경제", new ArrayList<>(), 0, false);
		SubField law = new SubField("법과생활", new ArrayList<>(), 0, false);
		SubField engineering = new SubField("공학의이해", new ArrayList<>(), 0, false);
		SubField foreign = new SubField("제2외국어와한문", new ArrayList<>(), 0, false);
		SubField artDesign = new SubField("예술과디자인", new ArrayList<>(), 0, false);

		Map<String, SubField> subFields = new HashMap<>();
		subFields.put(history.getField(), history);
		subFields.put(language.getField(), language);
		subFields.put(society.getField(), society);
		subFields.put(law.getField(), law);
		subFields.put(engineering.getField(), engineering);
		subFields.put(foreign.getField(), foreign);
		subFields.put(artDesign.getField(), artDesign);
		return subFields;
	}

	private boolean isAbeekCourse(CourseDto course) {
		return course != null && course.getAbeek().contains("MSC");
	}

	private boolean isSpecializedElective(CourseDto course) {
		return specializedElectiveNumber.contains(course.getNumber());
	}

	private boolean isRequiredMajor(CourseDto course, List<CourseDto> requiredCourses) {
		return requiredCourses.contains(course);
	}

	private List<CourseDto> getRequiredCourse(Major major) {
		return majorCourseRepository.findRequiredCoursesByMajor(major);
	}

	private int getStudentEnterYear(HttpServletRequest request) {
		return Integer.parseInt(cookieService.getStudentEnterFromCookie(request));
	}

	private Major getStudentMajor(HttpServletRequest request) {
//		return majorRepository.findMajorByCode(cookieService.getStudentMajorFromCookie(request));
		String majorCode = request.getParameter("major");
		return majorRepository.findMajorByCode(majorCode);
	}

	private boolean hasTakenInformationSystem(List<CourseDto> takenCourses) {
		return takenCourses.contains(new CourseDto("012304", 3));
	}

	private boolean hasTakenOOP(List<CourseDto> takenCourses) {
		return takenCourses.contains(new CourseDto("012305", 3));
	}

	private boolean hasTakenCProgramming(List<CourseDto> takenCourses) {
		return takenCourses.contains(new CourseDto("101810", 3));
	}

	private boolean hasTakenWebProgramming(List<CourseDto> takenCourses) {
		return takenCourses.contains(new CourseDto("012306", 3));
	}

	private String getCourseUrl(String type, String keyword) {
		return "/courses?" + "type=" + type + "&keyword=" + keyword;
	}

	private List<CourseDto> getMajorCourseList(Major major, int enterYear) {
		List<CourseDto> majorCourseList = majorHierarchyRepository.findAllMajorCoursesByMaster(major);
		String majorCode = major.getCode();
		if(majorCode.equals("CS")) {
			return getCSMajorCourseList(enterYear);
		}
		return majorCourseList;
	}

	private List<CourseDto> getCSMajorCourseList(int enterYear) {
		List<CourseDto> majorCourseList = majorHierarchyRepository.findAllMajorCoursesByMaster("CS");
		if(enterYear >= 20) {
			majorCourseList.removeIf(courseDto -> {
				String courseNumber = courseDto.getNumber();
				return courseNumber.equals("004174")
						|| courseNumber.equals("101810")
						|| courseNumber.equals("012305");
			});
		}
		return majorCourseList;
	}

	private boolean isMajorCSAndUnder19(String majorCode, int enterYear) {
		return majorCode.equals("CS") && enterYear <= 19;
	}
}
