/*
 * ZomboidDoc - Lua library compiler for Project Zomboid
 * Copyright (C) 2020-2021 Matthew Cain
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.cocolabs.pz.zdoc;

import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MainTest extends Main {

	@Test
	void shouldGetSafeLuaClassName() {

		registerClassOverride("Vector2", "JVector2");

		Assertions.assertEquals("JVector2", getSafeLuaClassName("Vector2"));
		Assertions.assertEquals("JVector2[]", getSafeLuaClassName("Vector2[]"));

		// class names that are not overriden should return same name
		Assertions.assertEquals("TestClass", getSafeLuaClassName("TestClass"));
	}

	@TestOnly
	public static void registerClassOverride(String key, String value) {
		CLASS_OVERRIDES.put(key, value);
	}
}
