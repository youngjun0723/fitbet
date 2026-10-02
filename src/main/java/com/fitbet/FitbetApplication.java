package com.fitbet;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FitbetApplication {

	public static void main(String[] args) {
		// PRD 8: JVM 타임존을 Asia/Seoul로 고정 (서버 OS 설정과 무관하게 동작)
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(FitbetApplication.class, args);
	}

}
