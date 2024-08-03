package com.michael.limit.management;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class LimitManagementApplicationTests {

	Calculator underTest = new Calculator();

	@Test
	void itShouldAddTwoNumber() {

		//Given
		int numberOne = 20;
		int numberTwo = 30;

		//When
		int result = underTest.add(numberOne, numberTwo);

		//Then
		int expected = 50;
		Assertions.assertThat(result).isEqualTo(expected);
	}

	static class Calculator {
		int add(int a, int b) {
			return a + b;
		}
	}

}
