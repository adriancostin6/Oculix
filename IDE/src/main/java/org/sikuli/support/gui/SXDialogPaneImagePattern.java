package org.sikuli.support.gui;

import java.awt.*;
/**
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */

public class SXDialogPaneImagePattern extends SXDialogIDE {
  public SXDialogPaneImagePattern(Point where, Object... parms) {
    super("sxidepaneimagepattern", where, parms);
  }

  public void optimize() {
    closeCancel();
  }
}
