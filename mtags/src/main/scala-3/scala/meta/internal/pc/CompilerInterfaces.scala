package scala.meta.internal.pc

import java.net.URI
import java.nio.file.Paths
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.{util => ju}
import scala.collection.JavaConverters._
import scala.io.Codec
import dotty.tools.dotc.interactive.InteractiveDriver
import dotty.tools.dotc.reporting.StoreReporter
import dotty.tools.dotc.parsing.Parsers.Parser
import dotty.tools.dotc.reporting.Message
import dotty.tools.dotc.reporting.StoreReporter
import dotty.tools.dotc.reporting.Diagnostic.Error
import dotty.tools.dotc.interfaces.Diagnostic
import dotty.tools.dotc.util.SourceFile
import dotty.tools.dotc.util.SourcePosition
import dotty.tools.io.VirtualFile
import org.eclipse.{lsp4j => l}

// note(@tgodzik) the plan is to be able to move the methods here back to Dotty compiler
// so that we can provide easier compatibility with multiple Scala 3 versions
object CompilerInterfaces {

  def parseErrors(
      driver: InteractiveDriver,
      uri: URI,
      text: String
  ): ju.List[l.Diagnostic] = {
    val sourceFile = toSource(uri, text)
    // we need to have a separate reporter otherwise errors are not cleared
    val parsingReporter = new StoreReporter(null)
    val parser = new Parser(sourceFile)(using driver.currentCtx.fresh.setReporter(parsingReporter))
    parser.parse()
    val diags = parsingReporter.allErrors
    diags.flatMap(diagnostic).asJava
  }

  private def diagnostic(mc: Error): Option[l.Diagnostic] =
    if (!mc.pos.exists)
      None // diagnostics without positions are not supported: https://github.com/Microsoft/language-server-protocol/issues/249
    else {
      def severity(level: Int): l.DiagnosticSeverity = {
        level match {
          case Diagnostic.INFO =>
            l.DiagnosticSeverity.Information
          case Diagnostic.WARNING =>
            l.DiagnosticSeverity.Warning
          case Diagnostic.ERROR =>
            l.DiagnosticSeverity.Error
        }
      }
      val message = mc.msg
      val code = message.errorId.errorNumber.toString
      range(mc.pos).map(r =>
        new l.Diagnostic(
          r,
          mc.message,
          severity(mc.level),
          /*source =*/ "",
          code
        )
      )
    }

  private def toSource(uri: URI, sourceCode: String): SourceFile = {
    val path = Paths.get(uri)
    val virtualFile = new VirtualFile(path.getFileName.toString, path.toString)
    val writer = new BufferedWriter(
      new OutputStreamWriter(virtualFile.output, "UTF-8")
    )
    writer.write(sourceCode)
    writer.close()
    new SourceFile(virtualFile, Codec.UTF8)
  }

  private def range(p: SourcePosition): Option[l.Range] = {
    if (p.exists) {
      Some(
        new l.Range(
          new l.Position(
            p.startLine,
            p.startColumn
          ),
          new l.Position(p.endLine, p.endColumn)
        )
      )
    } else {
      None
    }
  }
}
