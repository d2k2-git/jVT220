/**
 * jVT220 - Java VT220 terminal emulator.
 *
 * (C) Copyright 2012 - J.W. Janssen, <j.w.janssen@lxtreme.nl>.
 */
package nl.lxtreme.jvt220.terminal.vt220;


import nl.lxtreme.jvt220.terminal.*;


/**
 * Provides an implementation of {@link ICursor}.
 */
public class CursorImpl implements ICursor
{
  // VARIABLES

  private int m_blinkRate;
  private boolean m_visible;
  private int m_x;
  private int m_y;

  // CONSTRUCTORS

  /**
   * Creates a new {@link CursorImpl} instance.
   */
  public CursorImpl()
  {
    m_blinkRate = 500;
    m_visible = true;
    m_x = 0;
    m_y = 0;
  }

  // METHODS

  /**
   * {@inheritDoc}
   */
  @Override
  public ICursor clone()
  {
    try
    {
      CursorImpl clone = ( CursorImpl )super.clone();
      clone.m_blinkRate = m_blinkRate;
      clone.m_visible = m_visible;
      clone.m_x = m_x;
      clone.m_y = m_y;
      return clone;
    }
    catch ( CloneNotSupportedException e )
    {
      throw new RuntimeException( "Cloning not supported!?!" );
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getBlinkRate()
  {
    return m_blinkRate;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getX()
  {
    return m_x;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int getY()
  {
    return m_y;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isVisible()
  {
    return m_visible;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setBlinkRate( final int rate )
  {
    if ( rate < 0 )
    {
      throw new IllegalArgumentException( "Invalid blink rate!" );
    }
    m_blinkRate = rate;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setVisible( final boolean visible )
  {
    m_visible = visible;
  }

  /**
   * {@inheritDoc}
   */
  final void setPosition( final int x, final int y )
  {
    m_x = x;
    m_y = y;
  }
}
