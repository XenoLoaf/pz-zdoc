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
package io.yooksi.pz.zdoc.compile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.*;

import org.apache.commons.collections4.PredicateUtils;
import org.apache.commons.collections4.list.PredicatedList;
import org.apache.commons.collections4.set.PredicatedSet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jetbrains.annotations.Nullable;

import io.yooksi.pz.zdoc.Main;
import io.yooksi.pz.zdoc.doc.ZomboidAPIDoc;
import io.yooksi.pz.zdoc.doc.ZomboidJavaDoc;
import io.yooksi.pz.zdoc.doc.detail.DetailParsingException;
import io.yooksi.pz.zdoc.doc.detail.FieldDetail;
import io.yooksi.pz.zdoc.doc.detail.MethodDetail;
import io.yooksi.pz.zdoc.element.java.JavaClass;
import io.yooksi.pz.zdoc.element.java.JavaField;
import io.yooksi.pz.zdoc.element.java.JavaMethod;
import io.yooksi.pz.zdoc.element.mod.MemberModifier;
import io.yooksi.pz.zdoc.logger.Logger;
import io.yooksi.pz.zdoc.util.Utils;

public class JavaCompiler implements ICompiler<ZomboidJavaDoc> {

	public static final String GLOBAL_OBJECT_CLASS = "zombie.Lua.LuaManager.GlobalObject";
	private static final File SERIALIZE_LUA = new File("serialize.lua");

	private final Set<Class<?>> exposedJavaClasses;
	private final Set<String> excludedClasses;

	public JavaCompiler(Set<String> excludedClasses) throws CompilerException {
		try {
			/* serialize.lua file is required by J2SEPlatform when setting up environment,
			 * it is searched in project root directory and it will not be available there
			 * when running from IDE, so we have to make it available for runtime session
			 */
			Logger.debug("Initializing JavaCompiler...");
			if (!SERIALIZE_LUA.exists())
			{
				Logger.debug("Did not find serialize.lua file in root directory");
				try (InputStream iStream = Main.CLASS_LOADER.getResourceAsStream(SERIALIZE_LUA.getPath()))
				{
					if (iStream == null) {
						throw new IllegalStateException("Unable to find serialize.lua file");
					}
					Logger.debug("Copying serialize.lua file to root directory");
					FileUtils.copyToFile(iStream, SERIALIZE_LUA);
					if (!SERIALIZE_LUA.exists()) {
						throw new IOException("Unable to copy serialize.lua to root directory");
					}
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			exposedJavaClasses = Collections.unmodifiableSet(getExposedJava());
			/*
			 * delete serialize.lua file, we don't need it anymore,
			 * use deleteOnExit() only as a last resort if we can't delete right now
			 */
			if (!SERIALIZE_LUA.delete())
			{
				Logger.warn("Unable to delete serialize.lua, deleting on JVM exit");
				SERIALIZE_LUA.deleteOnExit();
			}
		}
		catch (ReflectiveOperationException e) {
			throw new CompilerException("Error occurred while reading exposed java", e);
		}
		this.excludedClasses = excludedClasses;
	}

	static List<JavaField> compileJavaFields(Class<?> clazz, @Nullable ZomboidAPIDoc doc) throws DetailParsingException {

		Logger.debug("Start compiling java fields...");
		List<JavaField> result = PredicatedList.predicatedList(
				new ArrayList<>(), PredicateUtils.notNullPredicate()
		);
		FieldDetail fieldDetail = doc != null ? new FieldDetail(doc) : null;
		for (Field field : clazz.getDeclaredFields())
		{
			String fieldName = field.getName();
			Logger.debug("Start compiling field %s...", fieldName);
			// synthetic fields are generated by compiler for internal purposes
			if (field.isSynthetic()) {
				continue;
			}
			int typeParamCount = field.getType().getTypeParameters().length;
			if (typeParamCount > 0)
			{
				/* if the field is a parameterized type we are not going to be able
				 * to determine the exact type due to runtime erasure, so try to
				 * use the field data from online API page if possible
				 */
				Logger.debug("Field has %d type parameters", typeParamCount);
				JavaClass jField = new JavaClass(field.getType());
				if (doc != null)
				{
					Logger.debug("Searching for field in document %s", doc.getName());
					JavaField docField = fieldDetail.getEntry(field.getName());
					if (docField != null)
					{
						/* extra care has to be taken to ensure that we are dealing with exactly
						 * the same object since API documentation is often out of date
						 */
						Logger.debug("Found mathing detail field entry with name %s", docField.getName());
						if (docField.getType().equals(jField, true))
						{
							/* matching field was found, use field data pulled from API page
							 * with written type parameters instead of declared field
							 */
							result.add(docField);
							continue;
						}
						else Logger.debug("Detail entry (%s) did not match field", docField.getType());
					}
					String format = "Didn't find matching field \"%s\" in document \"%s\"";
					Logger.detail(String.format(format, field.getName(), doc.getName()));
				}
				/* when no matching field or API page was found, construct new JavaField
				 * with same properties as declared field but make parameterized types null
				 */
				MemberModifier modifier = new MemberModifier(field.getModifiers());
				result.add(new JavaField(jField, field.getName(), modifier));
			}
			/* the field is not a parameterized type,
			 * use declared Field object to construct JavaField instance
			 */
			else
			{
				Logger.debug("Constructing field from JavaField instance");
				result.add(new JavaField(field));
			}
		}
		Logger.debug("Finished compiling %d fields", result.size());
		return result;
	}

	static Set<JavaMethod> compileJavaMethods(Class<?> clazz, @Nullable ZomboidAPIDoc doc) throws DetailParsingException {

		Logger.debug("Start compiling java methods...");
		Set<JavaMethod> result = PredicatedSet.predicatedSet(
				new HashSet<>(), PredicateUtils.notNullPredicate()
		);
		MethodDetail methodDetail = doc != null ? new MethodDetail(doc) : null;
		for (Method method : clazz.getDeclaredMethods())
		{
			String methodName = method.getName();
			Logger.debug("Start compiling method %s...", methodName);
			// synthetic methods are generated by compiler for internal purposes
			if (method.isSynthetic())
			{
				Logger.debug("Found synthetic method, will not compile");
				continue;
			}
			JavaMethod jMethod = new JavaMethod(method);
			if (doc != null)
			{
				Logger.debug("Searching for method in document %s", doc.getName());
				JavaMethod matchedMethod = null;
				Set<JavaMethod> methodEntries = methodDetail.getEntries(method.getName());
				Iterator<JavaMethod> iterator = methodEntries.iterator();
				while (matchedMethod == null && iterator.hasNext())
				{
					JavaMethod entry = iterator.next();
					if (entry.equals(jMethod, true)) {
						matchedMethod = entry;
					}
				}
				if (matchedMethod != null)
				{
					result.add(matchedMethod);
					continue;
				}
				String format = "Didn't find matching method \"%s\" in document \"%s\"";
				Logger.detail(String.format(format, methodName, doc.getName()));
			}
			else Logger.debug("Constructing method from JavaMethod instance");
			result.add(jMethod);
		}
		Logger.debug("Finished compiling %d methods", result.size());
		return result;
	}

	/**
	 * Initialize {@code LuaManager} and return a set of exposed Java classes.
	 *
	 * @return a set of exposed Java classes.
	 *
	 * @throws RuntimeException if the private field ({@code LuaManager.Exposer#exposed})
	 * 		holding the set of exposed Java classes could not be found.
	 */
	@SuppressWarnings("unchecked")
	static HashSet<Class<?>> getExposedJava() throws ReflectiveOperationException {

		/* use exclusively reflection to define classes and interact
		 * with class objects to allow CI workflow to compile project
		 */
		Logger.debug("Reading exposed java classes...");
		Class<?> luaManager = Utils.getClassForName("zombie.Lua.LuaManager");
		Class<?> zombieCore = Utils.getClassForName("zombie.core.Core");

		Class<?> j2SEPlatform = Utils.getClassForName("se.krka.kahlua.j2se.J2SEPlatform");
		Class<?> kahluaConverterManager = Utils.getClassForName(
				"se.krka.kahlua.converter.KahluaConverterManager"
		);
		Class<?> kahluaPlatform = Utils.getClassForName("se.krka.kahlua.vm.Platform");
		Class<?> kahluaTable = Utils.getClassForName("se.krka.kahlua.vm.KahluaTable");

		Class<?> exposerClass = Arrays.stream(luaManager.getDeclaredClasses())
				.filter(c -> c.getName().equals("zombie.Lua.LuaManager$Exposer"))
				.findFirst().orElseThrow(ClassNotFoundException::new);

		Object platform = ConstructorUtils.invokeConstructor(j2SEPlatform);
		Method newEnvironment = j2SEPlatform.getDeclaredMethod("newEnvironment");

		Constructor<?> constructor = exposerClass.getDeclaredConstructor(
				kahluaConverterManager,    // se.krka.kahlua.converter.KahluaConverterManager
				kahluaPlatform,            // se.krka.kahlua.vm.Platform
				kahluaTable                // se.krka.kahlua.vm.KahluaTable
		);
		constructor.setAccessible(true);
		Object exposer = constructor.newInstance(
				ConstructorUtils.invokeConstructor(kahluaConverterManager),
				j2SEPlatform.cast(platform), newEnvironment.invoke(platform)
		);
		Method exposeAll = MethodUtils.getMatchingMethod(exposerClass, "exposeAll");
		exposeAll.setAccessible(true);
		try {
			Field dDebug = zombieCore.getDeclaredField("bDebug");
			FieldUtils.writeStaticField(dDebug, true);
			exposeAll.invoke(exposer);
		}
		catch (InvocationTargetException e) {
			// this is expected
		}
		HashSet<Class<?>> result = (HashSet<Class<?>>) FieldUtils.readDeclaredField(
				exposer, "exposed", true
		);
		// class containing global exposed methods
		Logger.debug("Including global methods from %s", GLOBAL_OBJECT_CLASS);
		result.add(Utils.getClassForName(GLOBAL_OBJECT_CLASS));
		return result;
	}

	public Set<ZomboidJavaDoc> compile() {

		Logger.info("Start compiling java classes...");
		Set<ZomboidJavaDoc> result = new HashSet<>();
		for (Class<?> exposedClass : exposedJavaClasses)
		{
			String exposedClassName = exposedClass.getName();
			if (excludedClasses.removeIf(ec -> ec.equals(exposedClassName)))
			{
				Logger.detail("Excluding exposed class %s", exposedClassName);
				continue;
			}
			Logger.info("Compiling exposed class %s...", exposedClassName);
			String classPath = JavaClass.getPathForClass(exposedClass);
			if (!classPath.isEmpty())
			{
				@Nullable ZomboidAPIDoc document = null;
				try {
					Logger.debug(String.format("Getting API page for class \"%s\"", classPath));
					document = ZomboidAPIDoc.getPage(Paths.get(classPath));
					if (document == null) {
						Logger.detail(String.format("Unable to find API page for path %s", classPath));
					}
				}
				catch (IOException e)
				{
					String msg = "Error occurred while getting API page for path %s";
					Logger.error(String.format(msg, classPath), e);
				}
				JavaClass javaClass = new JavaClass(exposedClass);
				List<JavaField> javaFields;
				try {
					javaFields = compileJavaFields(exposedClass, document);
				}
				catch (DetailParsingException e)
				{
					String msg = "Error occurred while compiling java fields for document %s";
					Logger.error(String.format(msg, Objects.requireNonNull(document).getName()), e);
					continue;
				}
				Set<JavaMethod> javaMethods;
				try {
					javaMethods = compileJavaMethods(exposedClass, document);
				}
				catch (DetailParsingException e)
				{
					String msg = "Error occurred while compiling java methods for document %s";
					Logger.error(String.format(msg, Objects.requireNonNull(document).getName()), e);
					continue;
				}
				result.add(new ZomboidJavaDoc(javaClass, javaFields, javaMethods));
				Logger.detail("Compiled java class %s with %d fields and %d methods",
						exposedClassName, javaFields.size(), javaMethods.size());
			}
			else Logger.error(String.format("Unable to find path for Java class \"%s\", " +
					"might be an internal class.", exposedClass.getName()));
		}
		Logger.info("Finished compiling %d/%d java classes", result.size(), exposedJavaClasses.size());
		return result;
	}
}
