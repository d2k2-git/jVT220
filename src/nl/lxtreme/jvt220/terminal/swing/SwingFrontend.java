/**
 * jVT220 - Java VT220 terminal emulator.
 *
 * (C) Copyright 2012 - J.W. Janssen, <j.w.janssen@lxtreme.nl>.
 */
package nl.lxtreme.jvt220.terminal.swing;


import java.awt.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.image.*;
import java.io.*;
import java.text.*;
import java.util.List;

import javax.swing.*;

import nl.lxtreme.jvt220.terminal.*;
import nl.lxtreme.jvt220.terminal.ITerminal.ITextCell;


/**
 * Provides a Swing frontend for {@link ITerminal}.
 */
public class SwingFrontend extends JComponent implements ITerminalFrontend
{
  // INNER TYPES

  /**
   * Small container for the width, height and line spacing of a single
   * character.
   */
  static final class CharacterDimensions
  {
    final int m_height;
    final int m_width;
    final int m_lineSpacing;

    /**
     * Creates a new {@link CharacterDimensions} instance.
     * 
     * @param width
     *          the width of a single character, in pixels;
     * @param height
     *          the height of a single character, in pixels;
     * @param lineSpacing
     *          the spacing to use between two lines with characters, in pixels.
     */
    public CharacterDimensions( int width, int height, int lineSpacing )
    {
      m_width = width;
      m_height = height;
      m_lineSpacing = lineSpacing;
    }
  }

  /**
   * Asynchronous worker that reads data from an input stream and passes this to
   * the terminal backend.
   */
  final class InputStreamWorker extends SwingWorker<Void, Integer>
  {
    // VARIABLES

    private final InputStreamReader m_reader;
    private final CharBuffer m_buffer;

    // CONSTRUCTORS

    /**
     * Creates a new {@link InputStreamWorker} instance.
     */
    public InputStreamWorker( final InputStream inputStream ) throws IOException
    {
      m_reader = new InputStreamReader( inputStream, ISO8859_1 );
      m_buffer = new CharBuffer();
    }

    // METHODS

    @Override
    protected Void doInBackground() throws Exception
    {
      while ( !isCancelled() && !Thread.currentThread().isInterrupted() )
      {
        int r = m_reader.read();
        if ( r > 0 )
        {
          publish( Integer.valueOf( r ) );
        }
      }
      return null;
    }

    @Override
    protected void process( final List<Integer> readChars )
    {
      m_buffer.append( readChars );

      try
      {
        int n = m_terminal.read( m_buffer );

        m_buffer.removeUntil( n );

        repaint();
      }
      catch ( IOException exception )
      {
        exception.printStackTrace(); // XXX
      }
    }
  }

  private class SendLiteralAction extends AbstractAction
  {
    private final String m_literal;

    public SendLiteralAction( String literal )
    {
      m_literal = literal;
    }

    @Override
    public void actionPerformed( ActionEvent event )
    {
      writeCharacters( m_literal );
    }
  }

  // CONSTANTS

  /**
   * The default encoding to use for the I/O with the outer world.
   */
  private static final String ISO8859_1 = "ISO8859-1";

  // VARIABLES

  private ITerminalColorScheme m_colorScheme;
  private ICursor m_oldCursor;
  private volatile CharacterDimensions m_charDims;
  private volatile BufferedImage m_image;
  private volatile boolean m_listening;
  private ITerminal m_terminal;
  private InputStreamWorker m_inputStreamWorker;
  private Writer m_writer;

  // CONSTRUCTORS

  /**
   * Creates a new {@link SwingFrontend} instance.
   * 
   * @param aColumns
   *          the number of initial columns, > 0;
   * @param aLines
   *          the number of initial lines, > 0.
   */
  public SwingFrontend()
  {
    m_colorScheme = new XtermColorScheme();

    setFont( Font.decode( "Monospaced-PLAIN-14" ) );

    mapKeyboard();

    setEnabled( false );
    setFocusable( true );
    setFocusTraversalKeysEnabled( false ); // disables TAB handling
    requestFocus();
  }

  /**
   * Calculates the character dimensions for the given font, which is presumed
   * to be a monospaced font.
   * 
   * @param aFont
   *          the font to get the character dimensions for, cannot be
   *          <code>null</code>.
   * @return an array of length 2, containing the character width and height (in
   *         that order).
   */
  private static CharacterDimensions getCharacterDimensions( Font aFont )
  {
    BufferedImage im = new BufferedImage( 1, 1, BufferedImage.TYPE_INT_ARGB );

    Graphics2D g2d = im.createGraphics();
    g2d.setFont( aFont );
    FontMetrics fm = g2d.getFontMetrics();
    g2d.dispose();
    im.flush();

    int w = fm.charWidth( '@' );
    int h = fm.getAscent() + fm.getDescent();

    return new CharacterDimensions( w, h, fm.getLeading() + 1 );
  }

  // METHODS

  /**
   * Connects this frontend to a given input and output stream.
   * 
   * @param inputStream
   *          the input stream to connect to;
   * @param outputStream
   *          the output stream to connect to.
   * @throws IOException
   *           in case of I/O problems.
   */
  public void connect( InputStream inputStream, OutputStream outputStream ) throws IOException
  {
    disconnect();

    m_writer = new OutputStreamWriter( outputStream, ISO8859_1 );

    m_inputStreamWorker = new InputStreamWorker( inputStream );
    m_inputStreamWorker.execute();

    setEnabled( true );
  }

  /**
   * Disconnects this frontend from any input and output stream.
   * 
   * @throws IOException
   *           in case of I/O problems.
   */
  public void disconnect() throws IOException
  {
    try
    {
      if ( m_inputStreamWorker != null )
      {
        m_inputStreamWorker.cancel( true /* mayInterruptIfRunning */);
        m_inputStreamWorker = null;
      }
      if ( m_writer != null )
      {
        m_writer.close();
        m_writer = null;
      }
    }
    finally
    {
      setEnabled( false );
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Dimension getMaximumTerminalSize()
  {
    Rectangle bounds = getGraphicsConfiguration().getBounds();
    Insets insets = calculateTotalInsets();

    int width = bounds.width - insets.left - insets.right;
    int height = bounds.height - insets.top - insets.bottom;

    CharacterDimensions charDims = m_charDims;

    // Calculate the maximum number of columns & lines...
    int columns = width / charDims.m_width;
    int lines = height / ( charDims.m_height + charDims.m_lineSpacing );

    return new Dimension( columns, lines );
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Writer getWriter()
  {
    return m_writer;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isListening()
  {
    return m_listening;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setFont( Font font )
  {
    super.setFont( font );

    m_charDims = getCharacterDimensions( font );
  }

  /**
   * @see nl.lxtreme.jvt220.terminal.ITerminalFrontend#setReverse(boolean)
   */
  @Override
  public void setReverse( boolean reverse )
  {
    m_colorScheme.setInverted( reverse );
  }

  /**
   * Sets the size of this component in pixels. Overridden in order to redirect
   * this call to {@link #terminalSizeChanged(int, int)} with the correct number
   * of columns and lines.
   */
  @Override
  public void setSize( int width, int height )
  {
    Rectangle bounds = getGraphicsConfiguration().getBounds();
    Insets insets = calculateTotalInsets();

    if ( width == 0 )
    {
      width = bounds.width - insets.left - insets.right;
    }
    else if ( width < 0 )
    {
      width = getWidth();
    }
    if ( height == 0 )
    {
      height = bounds.height - insets.top - insets.bottom;
    }
    else if ( height < 0 )
    {
      height = getHeight();
    }

    CharacterDimensions charDims = m_charDims;

    int columns = width / charDims.m_width;
    int lines = height / ( charDims.m_height + charDims.m_lineSpacing );

    terminalSizeChanged( columns, lines );
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setTerminal( ITerminal terminal )
  {
    if ( terminal == null )
    {
      throw new IllegalArgumentException( "Terminal cannot be null!" );
    }
    m_terminal = terminal;
    m_terminal.setFrontend( this );
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void terminalChanged( ITextCell[] cells, boolean[] heatMap )
  {
    final int columns = m_terminal.getWidth();
    final int lines = m_terminal.getHeight();

    // Create copies of these data items to ensure they remain constant for
    // the remainer of this method...
    CharacterDimensions charDims = m_charDims;

    int cw = charDims.m_width;
    int ch = charDims.m_height;
    int ls = charDims.m_lineSpacing;

    if ( m_image == null )
    {
      // Ensure there's a valid image to paint on...
      terminalSizeChanged( columns, lines );
    }

    final Graphics2D canvas = m_image.createGraphics();
    canvas.setFont( getFont() );

    final Font font = getFont();
    final FontMetrics fm = canvas.getFontMetrics();
    final FontRenderContext frc = new FontRenderContext( null, true /* aa */, true /* fractionalMetrics */);

    if ( m_oldCursor != null )
    {
      drawCursor( canvas, m_oldCursor, m_colorScheme.getBackgroundColor() );
    }

    Color cursorColor = null;
    Rectangle repaintArea = null;

    for ( int i = 0; i < heatMap.length; i++ )
    {
      if ( heatMap[i] )
      {
        // Cell is changed...
        final ITextCell cell = cells[i];

        final int x = ( i % columns ) * cw;
        final int y = ( i / columns ) * ( ch + ls );

        final Rectangle rect = new Rectangle( x, y, cw, ch + ls );

        canvas.setColor( convertToColor( cell.getBackground(), m_colorScheme.getBackgroundColor() ) );
        canvas.fillRect( rect.x, rect.y, rect.width, rect.height );

        final String txt = Character.toString( cell.getChar() );

        AttributedString attrStr = new AttributedString( txt );
        cursorColor = applyAttributes( cell, attrStr, font );

        AttributedCharacterIterator characterIterator = attrStr.getIterator();
        LineBreakMeasurer measurer = new LineBreakMeasurer( characterIterator, frc );

        while ( measurer.getPosition() < characterIterator.getEndIndex() )
        {
          TextLayout textLayout = measurer.nextLayout( getWidth() );
          textLayout.draw( canvas, x, y + fm.getAscent() );
        }

        if ( repaintArea == null )
        {
          repaintArea = rect;
        }
        else
        {
          repaintArea = rect.intersection( repaintArea );
        }
      }
    }

    // Draw the cursor...
    m_oldCursor = m_terminal.getCursor().clone();
    if ( cursorColor == null )
    {
      cursorColor = m_colorScheme.getTextColor();
    }

    drawCursor( canvas, m_oldCursor, cursorColor );

    // Free the resources...
    canvas.dispose();

    if ( ( repaintArea != null ) && !repaintArea.isEmpty() )
    {
      repaint( repaintArea );
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void terminalSizeChanged( int columns, int lines )
  {
    final Dimension dims = calculateSizeInPixels( columns, lines );

    if ( ( m_image == null ) || ( m_image.getWidth() != dims.width ) || ( m_image.getHeight() != dims.height ) )
    {
      if ( m_image != null )
      {
        m_image.flush();
      }
      m_image = getGraphicsConfiguration().createCompatibleImage( dims.width, dims.height );

      Graphics2D canvas = m_image.createGraphics();

      try
      {
        canvas.setBackground( m_colorScheme.getBackgroundColor() );
        canvas.clearRect( 0, 0, m_image.getWidth(), m_image.getHeight() );
      }
      finally
      {
        canvas.dispose();
        canvas = null;
      }

      // Update the size of this component as well...
      Insets insets = getInsets();
      super.setSize( dims.width + insets.left + insets.right, dims.height + insets.top + insets.bottom );

      repaint( 50L );
    }
  }

  /**
   * Maps the keyboard to respond to keys like 'up', 'down' and the function
   * keys.
   */
  protected void mapKeyboard()
  {
    // TODO this mapping should come from the terminal!
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_UP, 0 ), "\033[A" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, 0 ), "\033[B" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, 0 ), "\033[C" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, 0 ), "\033[D" );

    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_HOME, 0 ), "\033[H" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_END, 0 ), "\033[F" );

    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_F1, 0 ), "\033[OP" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_F2, 0 ), "\033[OQ" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_F3, 0 ), "\033[OR" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_F4, 0 ), "\033[OS" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_F5, 0 ), "\033[[15~" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_F6, 0 ), "\033[[17~" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_F7, 0 ), "\033[[18~" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_F8, 0 ), "\033[[19~" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_F9, 0 ), "\033[[20~" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_F10, 0 ), "\033[[21~" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_F11, 0 ), "\033[[23~" );
    createKeyMapping( KeyStroke.getKeyStroke( KeyEvent.VK_F12, 0 ), "\033[[24~" );
  }

  /**
   * Creates a key mapping for the given keystroke and the given action which is
   * send as literal text to the terminal.
   * 
   * @param keystroke
   *          the keystroke to map, cannot be <code>null</code>;
   * @param action
   *          the action to map the keystroke to, cannot be <code>null</code>.
   */
  protected void createKeyMapping( KeyStroke keystroke, String action )
  {
    InputMap inputMap = getInputMap( WHEN_ANCESTOR_OF_FOCUSED_COMPONENT );
    inputMap.put( keystroke, action );

    ActionMap actionMap = getActionMap();
    actionMap.put( action, new SendLiteralAction( action ) );
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void paintComponent( Graphics canvas )
  {
    m_listening = false;

    canvas.setColor( m_colorScheme.getBackgroundColor() );

    Rectangle clip = canvas.getClipBounds();
    canvas.fillRect( clip.x, clip.y, clip.width, clip.height );

    try
    {
      Insets insets = getInsets();

      canvas.drawImage( m_image, insets.left, insets.top, null /* observer */);
    }
    finally
    {
      m_listening = true;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void processKeyEvent( KeyEvent event )
  {
    int id = event.getID();
    if ( id == KeyEvent.KEY_TYPED )
    {
      writeCharacter( event.getKeyChar() );
      event.consume();
    }

    super.processKeyEvent( event );
  }

  /**
   * Writes a given number of characters to the terminal.
   * 
   * @param chars
   *          the characters to write, cannot be <code>null</code>.
   */
  protected void writeCharacter( char ch )
  {
    try
    {
      if ( m_writer != null )
      {
        m_writer.write( ch );
        m_writer.flush();
      }
    }
    catch ( IOException e )
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  /**
   * Writes a given number of characters to the terminal.
   * 
   * @param chars
   *          the characters to write, cannot be <code>null</code>.
   */
  protected void writeCharacters( String chars )
  {
    try
    {
      if ( m_writer != null )
      {
        m_writer.write( chars );
        m_writer.flush();
      }
    }
    catch ( IOException e )
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  /**
   * Applies the attributes from the given {@link TextCell} to the given
   * {@link AttributedString}.
   * 
   * @param textCell
   *          the text cell to get the attributes from;
   * @param attributedString
   *          the {@link AttributedString} to apply the attributes to;
   * @param font
   *          the font to use.
   * @return the primary foreground color, never <code>null</code>.
   */
  private Color applyAttributes( ITextCell textCell, AttributedString attributedString, Font font )
  {
    Color fg = convertToColor( textCell.getForeground(), m_colorScheme.getTextColor() );
    Color bg = convertToColor( textCell.getBackground(), m_colorScheme.getBackgroundColor() );

    attributedString.addAttribute( TextAttribute.FAMILY, font.getFamily() );
    attributedString.addAttribute( TextAttribute.SIZE, font.getSize() );
    attributedString.addAttribute( TextAttribute.FOREGROUND, textCell.isReverse() ^ textCell.isHidden() ? bg : fg );
    attributedString.addAttribute( TextAttribute.BACKGROUND, textCell.isReverse() ? fg : bg );

    if ( textCell.isUnderline() )
    {
      attributedString.addAttribute( TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON );
    }
    if ( textCell.isBold() )
    {
      attributedString.addAttribute( TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD );
    }
    if ( textCell.isItalic() )
    {
      attributedString.addAttribute( TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE );
    }
    return textCell.isReverse() ^ textCell.isHidden() ? bg : fg;
  }

  /**
   * Calculates the size (in pixels) of the back buffer image.
   * 
   * @param columns
   *          the number of columns, > 0;
   * @param lines
   *          the number of lines, > 0.
   * @return a dimension with the image width and height in pixels.
   */
  private Dimension calculateSizeInPixels( int columns, int lines )
  {
    CharacterDimensions charDims = m_charDims;

    int width = ( columns * charDims.m_width );
    int height = ( lines * ( charDims.m_height + charDims.m_lineSpacing ) );
    return new Dimension( width, height );
  }

  /**
   * Calculates the total insets of this container and all of its parents.
   * 
   * @return the total insets, never <code>null</code>.
   */
  private Insets calculateTotalInsets()
  {
    // Take the screen insets as starting point...
    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets( getGraphicsConfiguration() );

    Container ptr = this;
    do
    {
      Insets compInsets = ptr.getInsets();
      insets.top += compInsets.top;
      insets.bottom += compInsets.bottom;
      insets.left += compInsets.left;
      insets.right += compInsets.right;
      ptr = ( Container )ptr.getParent();
    }
    while ( ptr != null );
    return insets;
  }

  /**
   * Converts a given color index to a concrete color value.
   * 
   * @param index
   *          the numeric color index, >= 0;
   * @param defaultColor
   *          the default color to use, cannot be <code>null</code>.
   * @return a color value, never <code>null</code>.
   */
  private Color convertToColor( int index, Color defaultColor )
  {
    if ( index < 1 )
    {
      return defaultColor;
    }
    return m_colorScheme.getColorByIndex( index - 1 );
  }

  /**
   * Draws the cursor on screen.
   * 
   * @param canvas
   *          the canvas to paint on;
   * @param cursor
   *          the cursor information;
   * @param color
   *          the color to paint the cursor in.
   */
  private void drawCursor( final Graphics canvas, final ICursor cursor, final Color color )
  {
    if ( !cursor.isVisible() )
    {
      return;
    }

    CharacterDimensions charDims = m_charDims;

    int cw = charDims.m_width;
    int ch = charDims.m_height;
    int ls = charDims.m_lineSpacing;

    int x = cursor.getX() * cw;
    int y = cursor.getY() * ( ch + ls );

    canvas.setColor( color );
    canvas.drawRect( x, y, cw, ch - 2 * ls );
  }
}
