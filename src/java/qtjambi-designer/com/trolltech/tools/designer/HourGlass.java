/****************************************************************************
**
** Copyright (C) 1992-2009 Nokia. All rights reserved.
**
** This file is part of Qt Jambi.
**
** ** $BEGIN_LICENSE$
** Commercial Usage
** Licensees holding valid Qt Commercial licenses may use this file in
** accordance with the Qt Commercial License Agreement provided with the
** Software or, alternatively, in accordance with the terms contained in
** a written agreement between you and Nokia.
** 
** GNU Lesser General Public License Usage
** Alternatively, this file may be used under the terms of the GNU Lesser
** General Public License version 2.1 as published by the Free Software
** Foundation and appearing in the file LICENSE.LGPL included in the
** packaging of this file.  Please review the following information to
** ensure the GNU Lesser General Public License version 2.1 requirements
** will be met: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html.
** 
** In addition, as a special exception, Nokia gives you certain
** additional rights. These rights are described in the Nokia Qt LGPL
** Exception version 1.0, included in the file LGPL_EXCEPTION.txt in this
** package.
** 
** GNU General Public License Usage
** Alternatively, this file may be used under the terms of the GNU
** General Public License version 3.0 as published by the Free Software
** Foundation and appearing in the file LICENSE.GPL included in the
** packaging of this file.  Please review the following information to
** ensure the GNU General Public License version 3.0 requirements will be
** met: http://www.gnu.org/copyleft/gpl.html.
** 
** If you are unsure which license is appropriate for your use, please
** contact the sales department at qt-sales@nokia.com.
** $END_LICENSE$

**
** This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
** WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
**
****************************************************************************/

package com.trolltech.tools.designer;

import com.trolltech.qt.core.QPoint;
import com.trolltech.qt.core.QPointF;
import com.trolltech.qt.core.QRect;
import com.trolltech.qt.core.QRectF;
import com.trolltech.qt.core.QSize;
import com.trolltech.qt.core.Qt;
import com.trolltech.qt.core.QTimerEvent;
import com.trolltech.qt.gui.QBrush;
import com.trolltech.qt.gui.QColor;
import com.trolltech.qt.gui.QHideEvent;
import com.trolltech.qt.gui.QMatrix;
import com.trolltech.qt.gui.QPainter;
import com.trolltech.qt.gui.QPainterPath;
import com.trolltech.qt.gui.QPaintEvent;
import com.trolltech.qt.gui.QPen;
import com.trolltech.qt.gui.QShowEvent;
import com.trolltech.qt.gui.QWidget;
import com.trolltech.qt.qreal.QReal;

public class HourGlass extends QWidget {

    public HourGlass() {
        this(null);
    }

    public HourGlass(QWidget parent) {
        super(parent);

        double mainRadius = 8;
        double maxRadius = 2.5;
        double minRadius = 1;
        path = new QPainterPath();
        QPointF pt = new QPointF(
         QReal.valueOf(0).platformValue(),
         QReal.valueOf(mainRadius - maxRadius).platformValue()
        );
        QMatrix m = new QMatrix();
        for (int i=0; i<8; ++i) {
            m.rotate(-360 / 8);
            double size = Math.max(maxRadius * 2 - i, minRadius * 2);
            QRectF r = new QRectF(
             QReal.valueOf(-size / 2).platformValue(),
             QReal.valueOf(-size / 2).platformValue(),
             QReal.valueOf(size).platformValue(),
             QReal.valueOf(size).platformValue()
            );
            r.moveCenter(m.map(pt));
            path.addEllipse(r);
        }
        hide();
    }

    public void start() {
        if (running)
            return;
        running = true;
        run(true);
        show();
    }

    public void stop() {
        running = false;
        run(false);
        hide();
    }

    @Override
    protected void showEvent(QShowEvent e) {
        run(true);
    }

    @Override
    protected void hideEvent(QHideEvent e) {
        run(false);
    }

    @Override
    protected void timerEvent(QTimerEvent e) {
        rotation += 360 / 8;
        update();
    }

    @Override
    protected void paintEvent(QPaintEvent arg) {
        QPainter p = new QPainter(this);
        p.setRenderHint(QPainter.RenderHint.Antialiasing);

        p.translate(rect().center());

        p.rotate(rotation);
        p.setBrush(new QBrush(new QColor(com.trolltech.qt.core.Qt.GlobalColor.gray)));
        p.setPen(QPen.NoPen);

        p.fillPath(path, new QBrush(new QColor(com.trolltech.qt.core.Qt.GlobalColor.gray)));
    }

    @Override
    public QSize sizeHint() {
        return new QSize(16, 16);
    }

    private void run(boolean visible) {
        if (visible && running && timerId == 0) {
            timerId = startTimer(100);
        } else if (timerId != 0 && (!visible || !running)) {
            killTimer(timerId);
            timerId = 0;
        }
    }

    private boolean running;
    private int timerId;
    private int rotation;
    private QPainterPath path;
}
