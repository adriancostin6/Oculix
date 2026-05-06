package org.sikuli.support.ide.autocomplete;

import org.sikuli.ide.EditorPane;
import org.sikuli.support.runner.IRunner;

import javax.swing.text.Caret;
/**
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */

public interface IAutoCompleter {

  String getName();

  void setPane(EditorPane pane);

  IRunner getRunner();

  void handle(Caret caret, int line, int pos, String lineText);

}
