/*******************************************************************************
 * Copyright (c) 2009, 2017 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.test.filter;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.test.TargetLoader;
import org.jacoco.core.test.filter.targets.Finally;
import org.jacoco.core.test.validation.Source;
import org.jacoco.core.test.validation.ValidationTestBase;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Test of filtering of duplicated bytecode that is generated for finally block.
 */
public class FinallyTest extends ValidationTestBase {

	private static boolean isJDK8 = !Pattern.compile("1\\.[567]\\.0_(\\d++)")
			.matcher(System.getProperty("java.version")).matches();

	public FinallyTest() {
		super(Finally.class);
	}

	/**
	 * {@link Finally#example(boolean)}
	 */
	@Test
	public void example() {
		if (isJDKCompiler) {
			assertLine("example.0", ICounter.EMPTY);
		} else {
			assertLine("example.0", ICounter.FULLY_COVERED);
		}
		assertLine("example.1", ICounter.FULLY_COVERED, 0, 2);
		assertLine("example.2", ICounter.FULLY_COVERED);
		assertLine("example.3", ICounter.EMPTY);
		assertLine("example.4", ICounter.EMPTY);
	}

	/**
	 * GOTO instructions at the end of duplicates of finally block might have
	 * line number of a last instruction of finally block and hence lead to
	 * unexpected coverage results, like for example in case of ECJ for
	 * {@link Finally#catchNotExecuted()}, {@link Finally#emptyCatch()}. So we
	 * decided to ignore them, even if they can correspond to a real break
	 * statement.
	 * <p>
	 * See also <a href=
	 * "https://bugs.openjdk.java.net/browse/JDK-8180141">JDK-8180141</a> and
	 * <a href=
	 * "https://bugs.openjdk.java.net/browse/JDK-7008643">JDK-7008643</a>.
	 * <p>
	 * {@link Finally#breakStatement()}
	 */
	@Test
	public void breakStatement() {
		assertLine("breakStatement", ICounter.EMPTY);

		assertLine("breakStatement.1", ICounter.FULLY_COVERED);
		assertLine("breakStatement.2", ICounter.EMPTY);
	}

	/**
	 * {@link Finally#catchNotExecuted()}
	 */
	@Test
	public void catchNotExecuted() {
		assertLine("catchNotExecuted.catch", ICounter.NOT_COVERED);
		assertLine("catchNotExecuted.0", ICounter.EMPTY);
		assertLine("catchNotExecuted.1", ICounter.FULLY_COVERED);
		assertLine("catchNotExecuted.2", ICounter.EMPTY);
	}

	/**
	 * {@link Finally#emptyCatch()}
	 */
	@Test
	public void emptyCatch() {
		assertLine("emptyCatch.0", ICounter.EMPTY);
		assertLine("emptyCatch.1", ICounter.FULLY_COVERED);
		assertLine("emptyCatch.2", ICounter.EMPTY);
	}

	/**
	 * {@link Finally#twoRegions()}
	 */
	@Test
	public void twoRegions() {
		assertLine("twoRegions.0", ICounter.EMPTY);
		if (isJDKCompiler && !isJDK8) {
			// https://bugs.openjdk.java.net/browse/JDK-7008643
			assertLine("twoRegions.1", ICounter.PARTLY_COVERED);
			assertLine("twoRegions.return.1", ICounter.EMPTY);
			assertLine("twoRegions.return.2", ICounter.EMPTY);
		} else {
			assertLine("twoRegions.1", ICounter.FULLY_COVERED);
			assertLine("twoRegions.return.1", ICounter.FULLY_COVERED);
			assertLine("twoRegions.return.2", ICounter.NOT_COVERED);
		}
		assertLine("twoRegions.2", ICounter.EMPTY);

		assertLine("twoRegions.if", ICounter.FULLY_COVERED);
		assertLine("twoRegions.region.1", ICounter.FULLY_COVERED);
		assertLine("twoRegions.region.2", ICounter.NOT_COVERED);
	}

	/**
	 * {@link Finally#nested()}
	 */
	@Test
	public void nested() {
		if (isJDKCompiler) {
			assertLine("nested.0", ICounter.EMPTY);
		} else {
			assertLine("nested.0", ICounter.FULLY_COVERED);
		}
		assertLine("nested.1", ICounter.EMPTY);
		assertLine("nested.2", ICounter.FULLY_COVERED);
		if (isJDKCompiler) {
			assertLine("nested.3", ICounter.EMPTY);
		} else {
			assertLine("nested.3", ICounter.FULLY_COVERED);
		}
		assertLine("nested.4", ICounter.FULLY_COVERED);
	}

	/**
	 * {@link Finally#emptyTry()}
	 */
	@Test
	public void emptyTry() {
		assertLine("emptyTry.0", ICounter.EMPTY);
		if (!isJDKCompiler || isJDK8) {
			assertLine("emptyTry.1", ICounter.FULLY_COVERED);
			assertLine("emptyTry.2", ICounter.EMPTY);
		} else {
			// compiler bug fixed in javac >= 1.8:
			assertLine("emptyTry.1", ICounter.PARTLY_COVERED);
			assertLine("emptyTry.2", ICounter.FULLY_COVERED);
		}
	}

	/**
	 * {@link Finally#alwaysCompletesAbruptly()}
	 */
	@Test
	public void alwaysCompletesAbruptly() {
		if (isJDKCompiler) {
			// uncovered case:
			assertLine("alwaysCompletesAbruptly.0", ICounter.EMPTY);
			assertLine("alwaysCompletesAbruptly.1", ICounter.PARTLY_COVERED);
		} else {
			assertLine("alwaysCompletesAbruptly.0", ICounter.PARTLY_COVERED);
			assertLine("alwaysCompletesAbruptly.1", ICounter.FULLY_COVERED);
		}
		assertLine("alwaysCompletesAbruptly.2", ICounter.EMPTY);
	}

	/**
	 * This test studies placement of GOTO instructions.
	 */
	@Test
	public void gotos() throws IOException {
		final Source source = Source.getSourceFor("src", Finally.class);

		final ClassNode classNode = new ClassNode();
		new ClassReader(TargetLoader.getClassDataAsBytes(Finally.class))
				.accept(classNode, 0);
		final Set<String> tags = new HashSet<String>();
		for (final MethodNode m : classNode.methods) {
			if ("main".equals(m.name)) {
				// skip it
				continue;
			}
			int lineNumber = -1;
			for (AbstractInsnNode i = m.instructions
					.getFirst(); i != null; i = i.getNext()) {
				if (AbstractInsnNode.LINE == i.getType()) {
					lineNumber = ((LineNumberNode) i).line;
				}
				if (Opcodes.GOTO == i.getOpcode()) {
					final String line = source.getLine(lineNumber);
					if (line.indexOf('$') < 0) {
						throw new AssertionError(
								"No tag at line " + lineNumber);
					}
					final String tag = line.substring(
							line.indexOf('$') + "$line-".length(),
							line.lastIndexOf('$'));
					tags.add(tag);
				}
			}
		}

		final Set<String> expected = new HashSet<String>();

		if (isJDKCompiler) {
			expected.add("example.2");
		} else {
			expected.add("example.0");
		}

		expected.add("breakStatement.for");
		if (isJDKCompiler) {
			expected.add("breakStatement.1");
			expected.add("breakStatement.2");
		} else {
			expected.add("breakStatement");
		}

		if (isJDKCompiler) {
			expected.add("emptyCatch.2");
		} else {
			expected.add("emptyCatch");
			expected.add("emptyCatch.1");
		}

		if (isJDKCompiler) {
			expected.add("catchNotExecuted.2");
		} else {
			expected.add("catchNotExecuted");
			expected.add("catchNotExecuted.1");
		}

		if (isJDKCompiler) {
			expected.add("nested.5");
			expected.add("nested.6");
		} else {
			expected.add("nested.0");
			expected.add("nested.3");
		}

		if (isJDKCompiler && !isJDK8) {
			expected.add("emptyTry.2");
		}

		if (!isJDKCompiler) {
			expected.add("alwaysCompletesAbruptly.0");
		}

		assertEquals(expected, tags);
	}

}
