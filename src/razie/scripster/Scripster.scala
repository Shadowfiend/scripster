/**  ____    __    ____  ____  ____/___     ____  __  __  ____
 *  (  _ \  /__\  (_   )(_  _)( ___) __)   (  _ \(  )(  )(  _ \           Read
 *   )   / /(__)\  / /_  _)(_  )__)\__ \    )___/ )(__)(  ) _ <     README.txt
 *  (_)\_)(__)(__)(____)(____)(____)___/   (__)  (______)(____/   LICENESE.txt
 */
package razie.scripster

import com.razie.pub.lightsoa._
import com.razie.pub.comms._
import com.razie.pub.base._
import com.razie.pub.base.data._
import com.razie.pub.http._
import com.razie.pub.http.sample._
import com.razie.pub.http.LightContentServer
import com.razie.pub.base.ExecutionContext
import razie.base._
import razie.base.scripting._

/** 
 * a door to the REPL
 * 
 * Usage: it works with a LightServer (see project razweb). You can {@link #attachTo} to an existing server or {@link create} a new, dedicated one.
 * 
 * If all you need in an app is the scripster, create one on a port of your choice, with no extra services.
 * 
 * To customize interaction, you can mess with the Repl - that's the actual interaction with the REPL
 * 
 * @author razvanc99
 */
object Scripster {

   // hook to the web/telnet server
   var contents : CS = null

   /** use this to attach the REPL to an existing server */
   def attachTo (ls:LightServer) = {
      contents = new CS(ls.contents.asInstanceOf[LightContentServer]) // TODO cleanup cast
      ls.contents = contents
   }
  
   /** create a new server on the specified port and start it on the thread */
   def create (port:Int, t:Option[(Runnable) => Thread], services:Seq[HttpSoaBinding] = Nil) {
      val ME = new AgentHandle("localhost", "localhost", "127.0.0.1", port
         .toString(), "http://localhost:" + port.toString());
      
      // stuff to set before you start the server
      HtmlRenderUtils.setTheme(new HtmlRenderUtils.DarkTheme());
      NoStatics.put(classOf[Agents], new Agents(new AgentCloud(ME), ME));

      val server = new LightServer (port, 20, ExecutionContext.instance(), new LightContentServer()) 
      val get = new MyServer()
      server.registerHandler(get)
      server.registerHandler(new LightCmdPOST(get))

      get.registerSoa(new HttpSoaBinding(ScriptService))
      services.foreach (get.registerSoa(_))
   
      attachTo(server)
  
      t match {
         case Some(mk) => mk (server).start()
         case None => server.run()
      }
   }
}

/** actual interaction with the REPL */
object Repl {
   ScriptFactory.init (new ScriptFactoryScala (null, true))
   
   def exec (lang:String, script:String, session:ScriptSession) : AnyRef = {
     session accumulate script
     
     val s = ScriptFactory.make ("scala", session.script)
     razie.Log ("execute script=" + session.script)
     
     s.interactive(session.ctx) match {
       case RazScript.SSucc(res) => {
          session.clear
          res.asInstanceOf[AnyRef]
       }
       case RazScript.SSuccNoValue => {
          session.clear
          null
       }
       case RazScript.SError(err) => {
          razie.Debug ("SError...: "+err)
          session.clear
          err
       }
       case RazScript.SIncomplete => {
          razie.Debug ("SIncomplete...accumulating: "+script)
          null
       }
       case RazScript.SIUnsupported => {
          // do the accumulation ourselves
         if (! session.inStatement) {
           val s = ScriptFactory.make ("scala", session.script)
           razie.Log ("execute script=" + session.script)
           session.clear
           s.eval(session.ctx)
          }
         else 
            null
       }
     }
   }
   
  def options (sessionId:String, line:String) = {
    Sessions get sessionId match {
       case Some(session) => {
    val l = session.ctx.options (line)

    import scala.collection.JavaConversions._
   
    val ret:List[razie.AI] = l.map (s=>razie.AI(s)).toList
    
    razie.Debug ("options for: \'"+line+"\' are: " +ret)
    ret
       }
       case None => Nil
    }
  }
}
