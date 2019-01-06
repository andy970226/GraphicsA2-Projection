package comp557.a2;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.vecmath.Matrix4d;
import javax.vecmath.Matrix4f;
import javax.vecmath.Point2d;

import com.jogamp.opengl.DebugGL2;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.gl2.GLUT;

import mintools.parameters.BooleanParameter;
import mintools.parameters.DoubleParameter;
import mintools.parameters.IntParameter;
import mintools.swing.ControlFrame;
import mintools.swing.VerticalFlowPanel;
import mintools.viewer.FlatMatrix4d;
import mintools.viewer.FlatMatrix4f;
import mintools.viewer.Interactor;
import mintools.viewer.TrackBallCamera;

/**
 * Assignment 2 - depth of field blur, and anaglyphys
 * 
 * For additional information, see the following paper, which covers
 * more on quality rendering, but does not cover anaglyphs.
 * 
 * The Accumulation Buffer: Hardware Support for High-Quality Rendering
 * Paul Haeberli and Kurt Akeley
 * SIGGRAPH 1990
 * 
 * http://http.developer.nvidia.com/GPUGems/gpugems_ch23.html
 * GPU Gems [2007] has a slightly more recent survey of techniques.
 *
 * @author AndiDai
 */
public class A2App implements GLEventListener, Interactor {


	private String name = "Comp 557 Assignment 2 - Andi Dai-260844907";
	
    /** Viewing mode as specified in the assignment */
    int viewingMode = 1;
        
    /** eye Z position in world coordinates */
    private DoubleParameter eyeZPosition = new DoubleParameter( "eye z", 0.25, 0, 3 ); 
    /** near plane Z position in world coordinates */
    private DoubleParameter nearZPosition = new DoubleParameter( "near z", 0.15, -0.2, 0.5 ); 
    /** far plane Z position in world coordinates */
    private DoubleParameter farZPosition  = new DoubleParameter( "far z", -0.25, -2, 0 ); 
    /** focal plane Z position in world coordinates */
    private DoubleParameter focalPlaneZPosition = new DoubleParameter( "focal z", 0, -1.5, 1.5 );     

    /** Samples for drawing depth of field blur */    
    private IntParameter samples = new IntParameter( "samples", 5, 1, 100 );   
    
    /** 
     * Aperture size for drawing depth of field blur
     * In the human eye, pupil diameter ranges between approximately 2 and 8 mm
     */
    private DoubleParameter aperture = new DoubleParameter( "aperture size", 0.003, 0, 0.01 );
    
    /** x eye offsets for testing (see objective 4) */         
    private DoubleParameter eyeXOffset = new DoubleParameter("eye offset in x", 0.0, -0.3, 0.3);
    /** y eye offsets for testing (see objective 4) */
    private DoubleParameter eyeYOffset = new DoubleParameter("eye offset in y", 0.0, -0.3, 0.3);
    
    private BooleanParameter drawCenterEyeFrustum = new BooleanParameter( "draw center eye frustum", false );    
    
    private BooleanParameter drawEyeFrustums = new BooleanParameter( "draw left and right eye frustums", false );
    
	/**
	 * The eye disparity should be constant, but can be adjusted to test the
	 * creation of left and right eye frustums or likewise, can be adjusted for
	 * your own eyes!! Note that 63 mm is a good inter occular distance for the
	 * average human, but you may likewise want to lower this to reduce the
	 * depth effect (images may be hard to fuse with cheap 3D colour filter
	 * glasses). Setting the disparity negative should help you check if you
	 * have your left and right eyes reversed!
	 */
    private DoubleParameter eyeDisparity = new DoubleParameter("eye disparity", 0.023, -0.1, 0.1 );

    private GLUT glut = new GLUT();
    GLU glu = new GLU();
    private Scene scene = new Scene();

    /**
     * Launches the application
     * @param args
     */
    public static void main(String[] args) {
        new A2App();
    }
    
    GLCanvas glCanvas;
    
    /** Main trackball for viewing the world and the two eye frustums */
    TrackBallCamera tbc = new TrackBallCamera();
    /** Second trackball for rotating the scene */
    TrackBallCamera tbc2 = new TrackBallCamera();
    
    /**
     * Creates the application
     */
    public A2App() {      
        Dimension controlSize = new Dimension(640, 640);
        Dimension size = new Dimension(640, 480);
        ControlFrame controlFrame = new ControlFrame("Controls");
        controlFrame.add("Camera", tbc.getControls());
        controlFrame.add("Scene TrackBall", tbc2.getControls());
        controlFrame.add("Scene", getControls());
        controlFrame.setSelectedTab("Scene");
        controlFrame.setSize(controlSize.width, controlSize.height);
        controlFrame.setLocation(size.width + 20, 0);
        controlFrame.setVisible(true);    
        GLProfile glp = GLProfile.getDefault();
        GLCapabilities glc = new GLCapabilities(glp);
        glCanvas = new GLCanvas( glc );
        glCanvas.setSize( size.width, size.height );
        glCanvas.setIgnoreRepaint( true );
        glCanvas.addGLEventListener( this );
        glCanvas.requestFocus();
        FPSAnimator animator = new FPSAnimator( glCanvas, 60 );
        animator.start();        
        tbc.attach( glCanvas );
        tbc2.attach( glCanvas );
        // initially disable second trackball, and improve default parameters given our intended use
        tbc2.enable(false);
        tbc2.setFocalDistance( 0 );
        tbc2.panRate.setValue(5e-5);
        tbc2.advanceRate.setValue(0.005);
        this.attach( glCanvas );        
        JFrame frame = new JFrame( name );
        frame.getContentPane().setLayout( new BorderLayout() );
        frame.getContentPane().add( glCanvas, BorderLayout.CENTER );
        frame.setLocation(0,0);        
        frame.addWindowListener( new WindowAdapter() {
            @Override
            public void windowClosing( WindowEvent e ) {
                System.exit(0);
            }
        });
        frame.pack();
        frame.setVisible( true );      
        
        
    }
    
    @Override
    public void dispose(GLAutoDrawable drawable) {
    	// nothing to do
    }
        
    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        // do nothing
    }
    
    @Override
    public void attach(Component component) {
        component.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() >= KeyEvent.VK_1 && e.getKeyCode() <= KeyEvent.VK_7) {
                    viewingMode = e.getKeyCode() - KeyEvent.VK_1 + 1;
                }
                // only use the tbc trackball camera when in view mode 1 to see the world from
                // first person view, while leave it disabled and use tbc2 ONLY FOR ROTATION when
                // viewing in all other modes
                if ( viewingMode == 1 ) {
                	tbc.enable(true);
                	tbc2.enable(false);
	            } else {
                	tbc.enable(false);
                	tbc2.enable(true);
	            }
            }
        });
    }
    
    /**
     * @return a control panel
     */
    public JPanel getControls() {     
        VerticalFlowPanel vfp = new VerticalFlowPanel();
        
        VerticalFlowPanel vfp2 = new VerticalFlowPanel();
        vfp2.setBorder(new TitledBorder("Z Positions in WORLD") );
        vfp2.add( eyeZPosition.getSliderControls(false));        
        vfp2.add( nearZPosition.getSliderControls(false));
        vfp2.add( farZPosition.getSliderControls(false));        
        vfp2.add( focalPlaneZPosition.getSliderControls(false));     
        vfp.add( vfp2.getPanel() );
        
        vfp.add ( drawCenterEyeFrustum.getControls() );
        vfp.add ( drawEyeFrustums.getControls() );        
        vfp.add( eyeXOffset.getSliderControls(false ) );
        vfp.add( eyeYOffset.getSliderControls(false ) );        
        vfp.add ( aperture.getSliderControls(false) );
        vfp.add ( samples.getSliderControls() );        
        vfp.add( eyeDisparity.getSliderControls(false) );
        VerticalFlowPanel vfp3 = new VerticalFlowPanel();
        vfp3.setBorder( new TitledBorder("Scene size and position" ));
        vfp3.add( scene.getControls() );
        vfp.add( vfp3.getPanel() );        
        return vfp.getPanel();
    }
             
    public void init( GLAutoDrawable drawable ) {
    	drawable.setGL( new DebugGL2( drawable.getGL().getGL2() ) );
        GL2 gl = drawable.getGL().getGL2();
        gl.glShadeModel(GL2.GL_SMOOTH);             // Enable Smooth Shading
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.5f);    // Black Background
        gl.glClearDepth(1.0f);                      // Depth Buffer Setup
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glEnable(GL2.GL_POINT_SMOOTH);
        gl.glEnable(GL2.GL_NORMALIZE );
        gl.glEnable(GL.GL_DEPTH_TEST);              // Enables Depth Testing
        gl.glDepthFunc(GL.GL_LEQUAL);               // The Type Of Depth Testing To Do 
        gl.glLineWidth( 2 );                        // slightly fatter lines by default!
        
    }   

	// TODO: Objective 1 - adjust for your screen resolution and dimension to something reasonable.
	double screenWidthPixels = 1920;
	double screenWidthMeters = 0.344;
	double metersPerPixel = screenWidthMeters / screenWidthPixels;
	FastPoissonDisk fastpoissondisk=new FastPoissonDisk();
    @Override
    public void display(GLAutoDrawable drawable) {        
        GL2 gl = drawable.getGL().getGL2();
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);                
		 
        
        double w = drawable.getSurfaceWidth() * metersPerPixel;
        double h = drawable.getSurfaceHeight() * metersPerPixel;
        double s=(eyeZPosition.getFloatValue()-nearZPosition.getFloatValue())/(eyeZPosition.getFloatValue());
    	
        
	  		
        if ( viewingMode == 1 ) {
        	// We will use a trackball camera, but also apply an 
        	// arbitrary scale to make the scene and frustums a bit easier to see
        	// (note the extra scale could have been part of the initializaiton of
        	// the tbc track ball camera, but this is eaiser)
            tbc.prepareForDisplay(drawable);
            gl.glScaled(15,15,15);        
          
            gl.glPushMatrix();
            tbc2.applyViewTransformation(drawable); // only the view transformation
            scene.display( drawable );
            gl.glBegin(GL2.GL_LINE_LOOP);
            gl.glColor3d(0.8,0.8,0);
            gl.glVertex3d( 0.5*w, 0.5*h,0);          
    		 gl.glVertex3d(-0.5*w, 0.5*h,0);          
    		 gl.glVertex3d(-0.5*w, -0.5*h, 0);          
    		 gl.glVertex3d( 0.5*w, -0.5*h, 0);  
    		 gl.glEnd();   //yellow
    		
    		 
    		 gl.glTranslated(eyeXOffset.getValue(),eyeYOffset.getValue(),eyeZPosition.getValue());
    	  		gl.glColor3d(0.99, 0.99, 0.99);
    	  		glut.glutSolidSphere(0.0125,50,50);//eye
            
            gl.glPopMatrix();

            if(drawCenterEyeFrustum.getValue())
            {	
            	FlatMatrix4d V = new FlatMatrix4d();
            	double offsetratio=(eyeZPosition.getFloatValue()-nearZPosition.getFloatValue())/(eyeZPosition.getFloatValue()-focalPlaneZPosition.getFloatValue());
            	
            	gl.glMatrixMode(GL2.GL_PROJECTION);
            	gl.glPushMatrix();                
                gl.glLoadIdentity();
                gl.glFrustum(-0.5*s*w-eyeXOffset.getFloatValue()*offsetratio , 0.5*s*w -eyeXOffset.getFloatValue()*offsetratio, -0.5*s*h-eyeYOffset.getFloatValue()*offsetratio,  0.5*s*h-eyeYOffset.getFloatValue()*offsetratio, eyeZPosition.getFloatValue()-nearZPosition.getFloatValue(),eyeZPosition.getFloatValue()-farZPosition.getFloatValue());
                gl.glGetDoublev(GL2.GL_PROJECTION_MATRIX,V.asArray(),0);
            	gl.glPopMatrix();
            	
            	
            	
            	
                gl.glMatrixMode(GL2.GL_MODELVIEW);                  
                gl.glPushMatrix();              
                V.reconstitute();
                V.getBackingMatrix().invert();                
             gl.glTranslatef(0, 0, eyeZPosition.getFloatValue()); 	
             gl.glTranslated(eyeXOffset.getValue(), eyeYOffset.getValue(), 0);
             gl.glMultMatrixd(V.asArray(), 0);
            	gl.glColor3d(1, 1, 1);
            	glut.glutWireCube(2);
            	gl.glPopMatrix();
            	
            	
            	
            	gl.glColor3d(0.5,0.5,0.5);
           
   		gl.glBegin(GL2.GL_LINE_LOOP);
   		double ratio;
   		ratio=(eyeZPosition.getFloatValue()-focalPlaneZPosition.getValue())/(eyeZPosition.getFloatValue()-nearZPosition.getValue());
   		gl.glVertex3d( 0.5*s*w*ratio, 0.5*s*h*ratio,focalPlaneZPosition.getValue());          
   		 gl.glVertex3d(-0.5*s*w*ratio, 0.5*s*h*ratio,focalPlaneZPosition.getValue());          
   		 gl.glVertex3d(-0.5*s*w*ratio, -0.5*s*h*ratio, focalPlaneZPosition.getValue());          
   		 gl.glVertex3d( 0.5*s*w*ratio, -0.5*s*h*ratio, focalPlaneZPosition.getValue());  
   		 gl.glEnd();
            }
            	
            
            // TODO: Objective 2 - draw camera frustum if drawCenterEyeFrustum is true
            
            // TODO: Objective 6 - draw left and right eye frustums if drawEyeFrustums is true
            if(drawEyeFrustums.getValue())
            {//gl.glLoadIdentity();
            	gl.glDisable(GL2.GL_LIGHTING) ;
            	gl.glMatrixMode(GL2.GL_MODELVIEW); 
                gl.glPushMatrix();
       		 gl.glTranslated(0.5*eyeDisparity.getValue(),0,eyeZPosition.getValue());
 	  		gl.glColor3d(0, 0.99, 0.99);
 	  		glut.glutSolidSphere(0.0125,50,50);
      		 gl.glTranslated(-eyeDisparity.getValue(),0,0);
	  		gl.glColor3d(1, 0, 0);
	  		glut.glutSolidSphere(0.0125,50,50);
 	  	  gl.glPopMatrix();
 	  	  
 	  
            	FlatMatrix4d VC = new FlatMatrix4d();
            //	double offsetratio=(eyeZPosition.getFloatValue()-nearZPosition.getFloatValue())/(eyeZPosition.getFloatValue()-focalPlaneZPosition.getFloatValue());
            	gl.glMatrixMode(GL2.GL_PROJECTION);
            	gl.glPushMatrix();                
                gl.glLoadIdentity();
                gl.glFrustum(-0.5*s*w-0.5*eyeDisparity.getFloatValue()*s , 0.5*s*w -0.5*eyeDisparity.getFloatValue()*s, -0.5*s*h,  0.5*s*h, eyeZPosition.getFloatValue()-nearZPosition.getFloatValue(),eyeZPosition.getFloatValue()-farZPosition.getFloatValue());
                
                gl.glGetDoublev(GL2.GL_PROJECTION_MATRIX,VC.asArray(),0);
            	gl.glPopMatrix();
                gl.glMatrixMode(GL2.GL_MODELVIEW);                  
                gl.glPushMatrix();              
                VC.reconstitute();
                VC.getBackingMatrix().invert();                
             gl.glTranslatef(0, 0, eyeZPosition.getFloatValue()); 	
             gl.glTranslated(0.5*eyeDisparity.getValue(),0, 0);
             gl.glMultMatrixd(VC.asArray(), 0);
            	gl.glColor3d(0, 1, 1);
       glut.glutWireCube(2);
           gl.glPopMatrix();
           
       	FlatMatrix4d VR = new FlatMatrix4d();
       	gl.glMatrixMode(GL2.GL_PROJECTION);
       	gl.glPushMatrix();                
           gl.glLoadIdentity();
           gl.glFrustum(-0.5*s*w+0.5*eyeDisparity.getFloatValue()*s , 0.5*s*w+0.5*eyeDisparity.getFloatValue()*s, -0.5*s*h,  0.5*s*h, eyeZPosition.getFloatValue()-nearZPosition.getFloatValue(),eyeZPosition.getFloatValue()-farZPosition.getFloatValue());
           
           gl.glGetDoublev(GL2.GL_PROJECTION_MATRIX,VR.asArray(),0);
       	gl.glPopMatrix();
           gl.glMatrixMode(GL2.GL_MODELVIEW);                  
           gl.glPushMatrix();              
           VR.reconstitute();
           VR.getBackingMatrix().invert();                
        gl.glTranslatef(0, 0, eyeZPosition.getFloatValue()); 	
        gl.glTranslated(-0.5*eyeDisparity.getValue(), 0, 0);
        gl.glMultMatrixd(VR.asArray(), 0);
       	gl.glColor3d(1, 0, 0);
  glut.glutWireCube(2);
      gl.glPopMatrix();
      gl.glEnable(GL2.GL_LIGHTING) ;  
            }
        } else if ( viewingMode == 2 ) {
  
        	gl.glMatrixMode(GL2.GL_PROJECTION);
        	gl.glPushMatrix();  
        	gl.glLoadIdentity();
        	gl.glFrustum(-0.5*s*w, 0.5*s*w , -0.5*s*h,  0.5*s*h, eyeZPosition.getFloatValue()-nearZPosition.getFloatValue(),eyeZPosition.getFloatValue()-farZPosition.getFloatValue());
        	 glu.gluLookAt(0, 0, eyeZPosition.getFloatValue(), 0,0, -1, 0, 1, 0);       
          //  gl.glFrustum(-0.5*w+eyeXOffset.getFloatValue(), 0.5*w+eyeXOffset.getFloatValue() , -0.5*h+eyeYOffset.getFloatValue(),  0.5*h+eyeYOffset.getFloatValue(), eyeZPosition.getFloatValue()-nearZPosition.getFloatValue(),eyeZPosition.getFloatValue()-farZPosition.getFloatValue());
            gl.glMatrixMode(GL2.GL_MODELVIEW); 
           gl.glPushMatrix();  
            gl.glLoadIdentity();             
           
        //    FastPoissonDisk disk=new FastPoissonDisk();
            scene.display( drawable );
           
            gl.glBegin(GL2.GL_LINE_LOOP);
            gl.glColor3d(0.8,0.8,0);
            gl.glVertex3d( 0.5*w, 0.5*h,0);          
    		 gl.glVertex3d(-0.5*w, 0.5*h,0);          
    		 gl.glVertex3d(-0.5*w, -0.5*h, 0);          
    		 gl.glVertex3d( 0.5*w, -0.5*h, 0);  
    		 gl.glEnd();
    		 
            gl.glPopMatrix();   
            
            
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPopMatrix();   
       
			
        	// TODO: Objective 2 - draw the center eye camera view
        	
        } else if ( viewingMode == 3 ) {  
        	int N=samples.getValue();
        	
        	Point2d p = new Point2d();
			
        	double offsetratio=(eyeZPosition.getFloatValue()-nearZPosition.getFloatValue())/(eyeZPosition.getFloatValue()-focalPlaneZPosition.getFloatValue());
        	for(int i=0;i<N;i++) {
        	fastpoissondisk.get(p, i, N);
        	p.x=p.x*aperture.getValue();
        	p.y=p.y*aperture.getValue();
        	gl.glMatrixMode(GL2.GL_PROJECTION);
        	gl.glPushMatrix();  
        	gl.glLoadIdentity();
        	gl.glFrustum(-0.5*s*w+p.x*offsetratio , 0.5*s*w+p.x*offsetratio, -0.5*s*h+p.y*offsetratio,  0.5*s*h+p.y*offsetratio, eyeZPosition.getFloatValue()-nearZPosition.getFloatValue(),eyeZPosition.getFloatValue()-farZPosition.getFloatValue());
        	
        	
        	gl.glMatrixMode(GL2.GL_MODELVIEW); 
           gl.glPushMatrix();  
           
            gl.glLoadIdentity();             
            glu.gluLookAt(-p.x, -p.y, eyeZPosition.getFloatValue(), 0,0, -100, 0, 1, 0);   
            scene.display( drawable ); 
            gl.glPopMatrix();                           
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPopMatrix();   
            System.out.println(i);
       
        	if(i==0)	
            	gl.glAccum( GL2.GL_LOAD, 1f/N );//to load the first image into the accumulation buffer
            	else
            	gl.glAccum( GL2.GL_ACCUM, 1f/N ); //to add the contribution of the other renders.
        	gl.glClear(GL2.GL_COLOR_BUFFER_BIT| GL2.GL_DEPTH_BUFFER_BIT);	
            	}
        	
        	
        	
        	gl.glAccum( GL2.GL_RETURN, 1 );
        	
        	//to copy the result back into the frame buffer once you're finished all rendering passes.
        	// TODO: Objective 5 - draw center eye with depth of field blur
           

			
			
        	
        	
        	
        	
        	
        } else if ( viewingMode == 4 ) {
        	
    //    	double offsetratio=(eyeZPosition.getFloatValue()-nearZPosition.getFloatValue())/(eyeZPosition.getFloatValue());
        	gl.glMatrixMode(GL2.GL_PROJECTION);
        	gl.glPushMatrix();  
        	gl.glLoadIdentity();
        	gl.glFrustum(-0.5*s*w+0.5*eyeDisparity.getFloatValue()*s , 0.5*s*w+0.5*eyeDisparity.getFloatValue()*s, -0.5*s*h,  0.5*s*h, eyeZPosition.getFloatValue()-nearZPosition.getFloatValue(),eyeZPosition.getFloatValue()-farZPosition.getFloatValue());
          gl.glMatrixMode(GL2.GL_MODELVIEW); 
           gl.glPushMatrix();  
           
            gl.glLoadIdentity();             
            glu.gluLookAt(-0.5*eyeDisparity.getValue(), 0, eyeZPosition.getFloatValue(), -0.5*eyeDisparity.getValue(),0, -100, 0, 1, 0);       
            scene.display( drawable );
            gl.glBegin(GL2.GL_LINE_LOOP);
            gl.glColor3d(0.8,0.8,0);
            gl.glVertex3d( 0.5*w, 0.5*h,0);          
    		 gl.glVertex3d(-0.5*w, 0.5*h,0);          
    		 gl.glVertex3d(-0.5*w, -0.5*h, 0);          
    		 gl.glVertex3d( 0.5*w, -0.5*h, 0);  
    		 gl.glEnd();
            gl.glPopMatrix();   
            
            
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPopMatrix();   
       
			
        	
        	
        	
            // TODO: Objective 6 - draw the left eye view
        	
        } else if ( viewingMode == 5 ) {  
        	
        	gl.glMatrixMode(GL2.GL_PROJECTION);
        	gl.glPushMatrix();  
        	gl.glLoadIdentity();
        	gl.glFrustum(-0.5*s*w-0.5*eyeDisparity.getFloatValue()*s , 0.5*s*w -0.5*eyeDisparity.getFloatValue()*s, -0.5*s*h,  0.5*s*h, eyeZPosition.getFloatValue()-nearZPosition.getFloatValue(),eyeZPosition.getFloatValue()-farZPosition.getFloatValue());
           gl.glMatrixMode(GL2.GL_MODELVIEW); 
           gl.glPushMatrix();  
            gl.glLoadIdentity();             
            glu.gluLookAt(0.5*eyeDisparity.getValue(), 0, eyeZPosition.getFloatValue(), 0.5*eyeDisparity.getValue(),0, -100, 0, 1, 0);       
            scene.display( drawable );
            gl.glBegin(GL2.GL_LINE_LOOP);
            gl.glColor3d(0.8,0.8,0);
            gl.glVertex3d( 0.5*w, 0.5*h,0);          
    		 gl.glVertex3d(-0.5*w, 0.5*h,0);          
    		 gl.glVertex3d(-0.5*w, -0.5*h, 0);          
    		 gl.glVertex3d( 0.5*w, -0.5*h, 0);  
    		 gl.glEnd();
            gl.glPopMatrix();   
            
            
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPopMatrix();   
       
        	// TODO: Objective 6 - draw the right eye view
        	                               
        } else if ( viewingMode == 6 ) {            
        	gl.glClear(GL2.GL_COLOR_BUFFER_BIT| GL2.GL_DEPTH_BUFFER_BIT ); 
        	
        	//double offsetratio=(eyeZPosition.getFloatValue()-nearZPosition.getFloatValue())/(eyeZPosition.getFloatValue());
        	gl.glMatrixMode(GL2.GL_PROJECTION);
        	gl.glPushMatrix();  
        	gl.glLoadIdentity();
        	gl.glFrustum(-0.5*s*w+0.5*eyeDisparity.getFloatValue()*s , 0.5*s*w+0.5*eyeDisparity.getFloatValue()*s, -0.5*s*h,  0.5*s*h, eyeZPosition.getFloatValue()-nearZPosition.getFloatValue(),eyeZPosition.getFloatValue()-farZPosition.getFloatValue());
          gl.glMatrixMode(GL2.GL_MODELVIEW); 
            gl.glColorMask( true, false, false, true );
            gl.glPushMatrix();  
            gl.glLoadIdentity();             
            glu.gluLookAt(-0.5*eyeDisparity.getValue(), 0, eyeZPosition.getFloatValue(), -0.5*eyeDisparity.getValue(),0, -100, 0, 1, 0);       

            scene.display( drawable );
            gl.glPopMatrix();   
                       
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPopMatrix();   
           
            
      
            gl.glColorMask( false, true, true, true );
            gl.glClear(GL2.GL_COLOR_BUFFER_BIT| GL2.GL_DEPTH_BUFFER_BIT ); 
            gl.glMatrixMode(GL2.GL_PROJECTION);
        	gl.glPushMatrix();  
        	gl.glLoadIdentity();
        	gl.glFrustum(-0.5*s*w-0.5*eyeDisparity.getFloatValue()*s , 0.5*s*w -0.5*eyeDisparity.getFloatValue()*s, -0.5*s*h,  0.5*s*h, eyeZPosition.getFloatValue()-nearZPosition.getFloatValue(),eyeZPosition.getFloatValue()-farZPosition.getFloatValue());
            gl.glMatrixMode(GL2.GL_MODELVIEW); 
            gl.glPushMatrix();     
            
            gl.glLoadIdentity();             
            glu.gluLookAt(0.5*eyeDisparity.getValue(), 0, eyeZPosition.getFloatValue(), 0.5*eyeDisparity.getValue(),0, -100, 0, 1, 0);                 
            scene.display( drawable );
            gl.glPopMatrix();   
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPopMatrix();   
            
      
            gl.glColorMask( true, true, true, false );
     
           
        	// TODO: Objective 7 - draw the anaglyph view using glColouMask
   		
        } else if ( viewingMode == 7 ) {  

        	int N=samples.getValue();
        	Point2d p = new Point2d();
		
        	for(int i=0;i<N;i++) {
        	fastpoissondisk.get(p, i, N);
        	p.x=p.x*aperture.getValue();
        	p.y=p.y*aperture.getValue();
        	

        	gl.glClear(GL2.GL_COLOR_BUFFER_BIT| GL2.GL_DEPTH_BUFFER_BIT ); 
        //	gl.glClearColor(0, 0, 0, 0);
        //	glEnable(GL2.gl_color_);
        	  
        	//gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT); 
        //	double eyeratio=(eyeZPosition.getFloatValue()-nearZPosition.getFloatValue())/(eyeZPosition.getFloatValue());
        	double offsetrandom=(eyeZPosition.getFloatValue()-nearZPosition.getFloatValue())/(eyeZPosition.getFloatValue()-focalPlaneZPosition.getFloatValue());
        	gl.glMatrixMode(GL2.GL_PROJECTION);
        	gl.glPushMatrix();  
        	gl.glLoadIdentity();
        	gl.glFrustum(-0.5*s*w+0.5*eyeDisparity.getFloatValue()*s-p.x*offsetrandom , 0.5*s*w+0.5*eyeDisparity.getFloatValue()*s-p.x*offsetrandom, -0.5*s*h-p.y*offsetrandom,  0.5*s*h-p.y*offsetrandom, eyeZPosition.getFloatValue()-nearZPosition.getFloatValue(),eyeZPosition.getFloatValue()-farZPosition.getFloatValue());
          gl.glMatrixMode(GL2.GL_MODELVIEW); 
            gl.glColorMask( true, false, false, true );
            gl.glPushMatrix();  
            gl.glLoadIdentity();             
            glu.gluLookAt(-0.5*eyeDisparity.getValue()+p.x, p.y, eyeZPosition.getFloatValue(), -0.5*eyeDisparity.getValue()+p.x,0, -100, 0, 1, 0);       

            scene.display( drawable );
            gl.glPopMatrix();   
                       
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPopMatrix();   
           
            
      
            gl.glColorMask( false, true, true, true );
            gl.glClear(GL2.GL_COLOR_BUFFER_BIT| GL2.GL_DEPTH_BUFFER_BIT ); 
            gl.glMatrixMode(GL2.GL_PROJECTION);
        	gl.glPushMatrix();  
        	gl.glLoadIdentity();
        	gl.glFrustum(-0.5*s*w-0.5*eyeDisparity.getFloatValue()*s-p.x*offsetrandom , 0.5*s*w -0.5*eyeDisparity.getFloatValue()*s-p.x*offsetrandom , -0.5*s*h-p.y*offsetrandom ,  0.5*s*h-p.y*offsetrandom , eyeZPosition.getFloatValue()-nearZPosition.getFloatValue(),eyeZPosition.getFloatValue()-farZPosition.getFloatValue());
            gl.glMatrixMode(GL2.GL_MODELVIEW); 
            gl.glPushMatrix();     
            
            gl.glLoadIdentity();             
            glu.gluLookAt(0.5*eyeDisparity.getValue()+p.x, p.y, eyeZPosition.getFloatValue(), 0.5*eyeDisparity.getValue()+p.x,0, -100, 0, 1, 0);                 
            scene.display( drawable );
            gl.glPopMatrix();   
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPopMatrix();   
            if(i==0)	
            	gl.glAccum( GL2.GL_LOAD, 1f/N );//to load the first image into the accumulation buffer
            	else
            	gl.glAccum( GL2.GL_ACCUM, 1f/N ); //to add the contribution of the other renders.
        	gl.glClear(GL2.GL_COLOR_BUFFER_BIT| GL2.GL_DEPTH_BUFFER_BIT);	
      
            gl.glColorMask( true, true, true, false );
     
        	}
        	gl.glAccum( GL2.GL_RETURN, 1 );
        	
        	// TODO: Bonus Ojbective 8 - draw the anaglyph view with depth of field blur
        	
        }        
    }
    
}
