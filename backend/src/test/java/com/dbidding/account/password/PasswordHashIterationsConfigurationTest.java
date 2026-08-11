package com.dbidding.account.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.FileSystemResource;

class PasswordHashIterationsConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void 반복횟수_환경변수가_없으면_안전한_기본값을_사용한다() throws IOException {
		YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
		MutablePropertySources propertySources = new MutablePropertySources();
		loader.load(
			"application",
			new FileSystemResource("src/main/resources/application.yml")
		).forEach(propertySources::addLast);

		PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

		assertThat(resolver.getProperty("app.password-hash.iterations", Integer.class)).isEqualTo(600_000);
	}

	@Test
	void 데모_반복횟수를_주입하면_k6_시드_해시를_검증한다() {
		contextRunner.withPropertyValues("app.password-hash.iterations=100")
			.run(context -> {
				PasswordHasher passwordHasher = new PasswordHasher(context.getBean(PasswordHashProperties.class));
			assertThat(passwordHasher.matches(
				"K6LoadTest123!",
				"6b362d6c6f61642d746573742d73616c",
				"9bf31158e6621e360af2186721ceb7337300ae425e0bfd587042165af6ec6ce7"
			)).isTrue();
			});
	}

	@Test
	void 설정을_명시하지_않은_컨텍스트도_안전한_기본값을_사용한다() {
		contextRunner.run(context -> assertThat(context)
			.hasNotFailed()
			.getBean(PasswordHashProperties.class)
			.extracting(PasswordHashProperties::iterations)
			.isEqualTo(600_000));
	}

	@Test
	void 반복횟수가_0이면_애플리케이션_시작에_실패한다() {
		contextRunner.withPropertyValues("app.password-hash.iterations=0")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure())
					.hasRootCauseInstanceOf(IllegalArgumentException.class)
					.hasRootCauseMessage("Password hash iterations must be positive");
		});
	}

	@Test
	void 반복횟수가_숫자가_아니면_애플리케이션_시작에_실패한다() {
		contextRunner.withPropertyValues("app.password-hash.iterations=demo")
			.run(context -> assertThat(context).hasFailed());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(PasswordHashProperties.class)
	static class TestConfiguration {
	}
}
