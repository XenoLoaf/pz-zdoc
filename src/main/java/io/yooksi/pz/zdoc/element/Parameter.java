/*
 * ZomboidDoc - Project Zomboid API parser and lua compiler.
 * Copyright (C) 2020 Matthew Cain
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
package io.yooksi.pz.zdoc.element;

import io.yooksi.pz.zdoc.doc.JavaDoc;
import io.yooksi.pz.zdoc.lang.EmmyLua;

/**
 * This class represents parsed method parameter.
 */
public class Parameter {

	private final String type;
	private final String name;

	public Parameter(String type, String name) {

		type = type.trim();
		// ensure built-in types are lower-cased
		this.type = EmmyLua.getSafeType(type);

		name = name.trim();
		// ensure parameter name is not a reserved lua keyword
		this.name = EmmyLua.getSafeKeyword(name);
	}

	@Override
	public String toString() {
		return (type + ' ' + name).trim();
	}

	public String getType(boolean qualified) {
		return qualified ? type : JavaDoc.Parser.removeElementQualifier(type);
	}

	public String getName(boolean qualified) {
		return qualified ? name : JavaDoc.Parser.removeElementQualifier(name);
	}

	public Parameter getUnqualified() {
		return new Parameter(getType(false), getName(false));
	}

	public Parameter copy() {
		return new Parameter(type, name);
	}
}