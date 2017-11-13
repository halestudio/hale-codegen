/*
 * Copyright (c) 2016 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     wetransform GmbH <http://www.wetransform.to>
 */

package to.wetransform.hale.codegen.generator;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.Test;

public class CLITest {

  private static final boolean DELETE_FILES = false;

  @Test
  public void testGenerateSimple() throws Exception {
    Path tempDir = Files.createTempDirectory("classes");

    CLI.run(getClass().getResource("/simple/city.xsd").toURI(), tempDir.toFile());

    // delete generated classes
    if (DELETE_FILES) {
      Files.walkFileTree(tempDir, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }

      });
    } else {
      System.out.println(tempDir.toAbsolutePath().toString());
    }
  }

  @Test
  public void testGenerateGeometry() throws Exception {
    Path tempDir = Files.createTempDirectory("classes");

    CLI.run(getClass().getResource("/geometry/hydroEx.xsd").toURI(), tempDir.toFile());

    // delete generated classes
    if (DELETE_FILES) {
      Files.walkFileTree(tempDir, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }

      });
    } else {
      System.out.println(tempDir.toAbsolutePath().toString());
    }
  }

}
