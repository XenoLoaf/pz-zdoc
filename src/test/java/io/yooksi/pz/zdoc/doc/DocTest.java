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
package io.yooksi.pz.zdoc.doc;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;

import org.junit.jupiter.api.Tag;

@Tag("doc")
public abstract class DocTest {

	protected static final ZomboidAPIDoc document;
	protected static final Path documentPath;

	static {
		try {
			URL resource = DocTest.class.getClassLoader().getResource("Test.html");
			documentPath = new File(Objects.requireNonNull(resource).toURI()).toPath();
			document = ZomboidAPIDoc.getLocalPage(documentPath);
			document.getDocument().setBaseUri(ZomboidAPIDoc.resolveURL("zombie/Test.html").toString());
		}
		catch (URISyntaxException | IOException e) {
			throw new RuntimeException(e);
		}
	}
}
