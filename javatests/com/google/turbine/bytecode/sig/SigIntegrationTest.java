/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.turbine.bytecode.sig;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.io.MoreFiles.getFileExtension;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.turbine.bytecode.ClassFile;
import com.google.turbine.bytecode.ClassReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Reads all field, class, and method signatures in the bootclasspath, and round-trips them through
 * {@link SigWriter} and {@link SigParser}.
 */
@RunWith(JUnit4.class)
public class SigIntegrationTest {

  private static final Splitter CLASS_PATH_SPLITTER =
      Splitter.on(File.pathSeparatorChar).omitEmptyStrings();

  void forEachBootclass(Consumer<Path> consumer) throws IOException {
    ImmutableList<Path> bootclasspath =
        Streams.stream(
                CLASS_PATH_SPLITTER.split(
                    Optional.ofNullable(System.getProperty("sun.boot.class.path")).orElse("")))
            .map(Paths::get)
            .filter(Files::exists)
            .collect(toImmutableList());
    if (!bootclasspath.isEmpty()) {
      for (Path path : bootclasspath) {
        Map<String, ?> env = new HashMap<>();
        try (FileSystem jarfs = FileSystems.newFileSystem(URI.create("jar:" + path.toUri()), env);
            Stream<Path> stream = Files.walk(jarfs.getPath("/"))) {
          stream
              .filter(Files::isRegularFile)
              .filter(p -> getFileExtension(p).equals("class"))
              .forEachOrdered(consumer);
        }
      }
      return;
    }
    {
      Map<String, ?> env = new HashMap<>();
      try (FileSystem fileSystem = FileSystems.newFileSystem(URI.create("jrt:/"), env);
          Stream<Path> stream = Files.walk(fileSystem.getPath("/modules"))) {
        stream.filter(p -> getFileExtension(p).equals("class")).forEachOrdered(consumer);
      }
    }
  }

  @Test
  public void roundTrip() throws Exception {
    int[] totalSignatures = {0};
    forEachBootclass(
        path -> {
          try {
            ClassFile classFile = ClassReader.read(path.toString(), Files.readAllBytes(path));
            {
              String signature = classFile.signature();
              if (signature != null) {
                assertThat(SigWriter.classSig(new SigParser(signature).parseClassSig()))
                    .isEqualTo(signature);
                totalSignatures[0]++;
              }
            }
            for (ClassFile.FieldInfo field : classFile.fields()) {
              String signature = field.signature();
              if (signature != null) {
                assertThat(SigWriter.type(new SigParser(signature).parseFieldSig()))
                    .isEqualTo(signature);
                totalSignatures[0]++;
                SigParser sigParser = new SigParser(signature); // Initialize based on your implementation

//                try {
//                  sigParser.parseFieldSig();
//                  fail("Expected AssertionError to be thrown");
//                } catch (AssertionError e) {
//                  assertEquals("Expected AssertionError message", "Some message here", e.getMessage());
//                  // Add more assertions if necessary
//                }
              }
            }
            for (ClassFile.MethodInfo method : classFile.methods()) {
              String signature = method.signature();
              if (signature != null) {
                assertThat(SigWriter.method(new SigParser(signature).parseMethodSig()))
                    .isEqualTo(signature);
                totalSignatures[0]++;
              }
            }
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
    // sanity-check that the bootclasspath contains a plausible number of signatures; 8u60 has >18k
    assertThat(totalSignatures[0]).isGreaterThan(10000);
  }
  @Test
  public void testParseFieldSigDefaultCase() {
    SigParser sigParser = mock(SigParser.class); // You should initialize it based on your implementation
    // Mock the behavior of peek() method to force the default case
    when(sigParser.peek()).thenReturn('X'); // 'X' represents any value that does not match the cases in the switch statement

    try {
      sigParser.parseFieldSig();
      fail("Expected AssertionError to be thrown");
    } catch (AssertionError e) {
      // Add assertions here
      assertEquals("Assertion error message", "Some message here", e.getMessage());
    }
  }
}
