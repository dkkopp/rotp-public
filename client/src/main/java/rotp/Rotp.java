/*
 * Copyright 2015-2020 Ray Fowler
 * 
 * Licensed under the GNU General Public License, Version 3 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     https://www.gnu.org/licenses/gpl-3.0.html
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rotp;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URISyntaxException;
import javax.swing.JFrame;
import rotp.model.game.GameSession;
import rotp.ui.BasePanel;
import rotp.ui.RotPUI;
import rotp.ui.SwingExceptionHandler;
import rotp.ui.UserPreferences;
import rotp.util.FontManager;

public class Rotp
{
  public static int IMG_W = 1229;
  public static int IMG_H = 768;
  public static String jarFileName = "Remnants.jar";
  public static String exeFileName = "Remnants.exe";
  public static boolean countWords = false;
  private static String startupDir = System.getProperty("startupdir");
  private static JFrame frame;
  public static String releaseId = "1.04";
  public static long startMs = System.currentTimeMillis();
  public static long maxHeapMemory = Runtime.getRuntime().maxMemory() / 1048576;
  public static long maxUsedMemory;
  public static boolean logging = false;
  private static float resizeAmt = -1.0f;
  public static int actualAlloc = -1;
  public static boolean reloadRecentSave = false;

  static GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

  public static void main(String[] args)
  {
    frame = new JFrame("Remnants of the Precursors");
    String loadSaveFile = "";
    if (args.length == 0)
    {
      logging = false;
    }
    else
    {
      if (args[0].toLowerCase().endsWith(".rotp"))
      {
        loadSaveFile = args[0];
      }
    }

    reloadRecentSave = containsArg(args, "reload");
    logging = containsArg(args, "log");
    Thread.setDefaultUncaughtExceptionHandler(new SwingExceptionHandler());
    frame.addWindowListener(new WindowAdapter()
    {
      @Override
      public void windowClosing(WindowEvent e)
      {
        System.exit(0);
      }
    });

    // note: referencing the RotPUI class executes its static block
    // which loads in sounds, images, etc
    frame.setLayout(new BorderLayout());
    frame.add(RotPUI.instance(), BorderLayout.CENTER);

    if (UserPreferences.fullScreen())
    {
      frame.setUndecorated(true);
      device.setFullScreenWindow(frame);
      resizeAmt();
    }
    else if (UserPreferences.borderless())
    {
      frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
      frame.setUndecorated(true);
      resizeAmt();
    }
    else
    {
      frame.setResizable(false);
      device.setFullScreenWindow(null);
      setFrameSize();
    }

    // this will not catch 32-bit JREs on all platforms, but better than nothing
    String bits = System.getProperty("sun.arch.data.model").trim();
    if (bits.equals("32"))
    {
      RotPUI.instance().mainUI().showJava32BitPrompt();
    }
    else if (reloadRecentSave)
    {
      GameSession.instance().loadRecentSession(false);
    }
    else if (!loadSaveFile.isEmpty())
    {
      GameSession.instance().loadSession("", loadSaveFile, false);
    }

    becomeVisible();
  }

  public static void becomeVisible()
  {
    frame.setVisible(true);
  }

  public static boolean containsArg(String[] argList, String key)
  {
    for (String s : argList)
    {
      if (s.equalsIgnoreCase(key))
      {
        return true;
      }
    }
    return false;
  }

  public static void setFrameSize()
  {
    resizeAmt = -1;
    double adj = resizeAmt();
    int vFrame = 0;
    int hFrame = 0;
    int maxX = (int) ((hFrame + IMG_W) * adj);
    int maxY = (int) ((vFrame + IMG_H) * adj);
    FontManager.current().resetFonts();
    if (logging)
    {
      System.out.println("setting size to: " + maxX + " x " + maxY);
    }
    frame.getContentPane().setPreferredSize(new Dimension(maxX, maxY));
    frame.pack();
  }

  public static float resizeAmt()
  {
    int pct = UserPreferences.windowed() ? UserPreferences.screenSizePct() : 100;
    float sizeAdj = (float) pct / 100.0f;
    if (resizeAmt < 0)
    {
      Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
      int sizeW = (int) (sizeAdj * size.width);
      int sizeH = (int) (sizeAdj * size.height);
      int maxX = sizeH * 8 / 5;
      int maxY = sizeW * 5 / 8;
      if (maxY > sizeH)
      {
        maxY = maxX * 5 / 8;
      }

      resizeAmt = (float) maxY / 768;
      (new BasePanel()).loadScaledIntegers();
      if (logging)
      {
        System.out.println("resize amt:" + resizeAmt);
      }
    }
    return resizeAmt;
  }

  public static String jarPath()
  {
    if (startupDir == null)
    {
      try
      {
        File jarFile = new File(Rotp.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        startupDir = jarFile.getParentFile().getPath();
      }
      catch (URISyntaxException ex)
      {
        System.out.println("Unable to resolve jar path: " + ex.toString());
        startupDir = ".";
      }
    }
    return startupDir;
  }

  public static void restart()
  {
    throw new UnsupportedOperationException("DENIED!!!!");
  }
}
