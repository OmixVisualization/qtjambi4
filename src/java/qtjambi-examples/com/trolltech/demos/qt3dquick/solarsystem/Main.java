/****************************************************************************
**
** Copyright (C) 1992-2009 Nokia. All rights reserved.
** Copyright (C) 2013 Peter Droste. All rights reserved.
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
package com.trolltech.demos.qt3dquick.solarsystem;

import com.trolltech.examples.QtJambiExample;
import com.trolltech.qt.core.QSize;
import com.trolltech.qt.core.QUrl;
import com.trolltech.qt.declarative.QDeclarativeError;
import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QIcon;
import com.trolltech.qt.gui.QMainWindow;
import com.trolltech.qt.gui.QWidget;
import com.trolltech.qt.qt3dquick.QDeclarativeView3D;

@QtJambiExample(name = "Qt3DQuick Solar System", canInstantiate = "call-static-method:checkQt3DQuickSupport")
public class Main extends QMainWindow{
	
	public Main(QWidget parent) {
		super(parent);
		createView(this);
	}
	
	public static boolean checkQt3DQuickSupport(){
		try {
            Class.forName("com.trolltech.qt.qt3dquick.QDeclarativeView3D");
            return true;
        } catch (Exception e) {
        }
        return false;
	}
	
	public void setSize(QSize size){
		this.resize(size);
	}

	public static void main(String args[])
	{
	    QApplication.initialize(args);
	    QWidget view = createView(null);
	    if (QApplication.arguments().contains("-maximize"))
	        view.showMaximized();
	    else if (QApplication.arguments().contains("-fullscreen"))
	        view.showFullScreen();
	    else
	        view.show();
	    // without deleting the view reference before execStatic() the application crashes at quit().
	    // this is because the view object and all child objects exist longer than the QApplication instance. 
	    view = null;
	    QApplication.execStatic();
	    QApplication.shutdown();
	}

	private static QWidget createView(QMainWindow parent){
		QDeclarativeView3D view = new QDeclarativeView3D(parent);
		view.engine().setOutputWarningsToStandardError(true);
		if(parent!=null){
			view.engine().quit.connect(parent, "close()");
			parent.setWindowTitle("Solar System");
			parent.setWindowIcon(new QIcon("classpath:/com/trolltech/demos/qt3dquick/quick3d.png"));
			parent.setCentralWidget(view);
			view.sceneResized.connect(parent, "setSize(QSize)");
		}else{
			view.engine().quit.connect(QApplication.instance(), "quit()");
		    view.setWindowTitle("Solar System");
		    view.setWindowIcon(new QIcon("classpath:/com/trolltech/demos/qt3dquick/quick3d.png"));
		}
	    view.setSource(new QUrl("classpath:/com/trolltech/demos/qt3dquick/solarsystem/qml/solarsystem.qml"));
	    for(QDeclarativeError error : view.errors()){
	    	System.out.println(error);
	    }
	    return view;
	}
}