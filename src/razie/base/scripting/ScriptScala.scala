/**  ____    __    ____  ____  ____/___     ____  __  __  ____
 *  (  _ \  /__\  (_   )(_  _)( ___) __)   (  _ \(  )(  )(  _ \           Read
 *   )   / /(__)\  / /_  _)(_  )__)\__ \    )___/ )(__)(  ) _ <     README.txt
 *  (_)\_)(__)(__)(____)(____)(____)___/   (__)  (______)(____/   LICENESE.txt
 */
package razie.base.scripting

import scala.tools.{nsc => nsc}
import scala.tools.nsc.{ InterpreterResults => IR }

/** will cache the environment */
class ScalaScriptContext (parent:ScriptContext = null) extends ScriptContextImpl (parent) {
   val env = new nsc.Settings (err)
   val p = new RaziesInterpreter (env)         
//   val p = new nsc.Interpreter (env)         
   
   lazy val c = {
      // not just create, but prime this ... first time it doesn't work...
      SS.bind (this, p)
      val cc = new nsc.interpreter.Completion(p)
      var scr = "java.lang.Sys"
      val l = new java.util.ArrayList[String]()
      cc.jline.complete (scr, scr.length-1, l)
      cc
   }
   
      /** content assist options */
   override def options (scr:String) : java.util.List[String] = {
      SS.bind (this, p)
      val l = new java.util.ArrayList[String]()
      c.jline.complete (scr, scr.length, l)
      val itDoesntWorkOtherwise = l.toString
      l
   }
   
   def err (s:String) : Unit = { lastError = s }
   
   var expr : Boolean = true // I'm in expression mode versus interpret mode
}

object SS {
  def bind (ctx:ScriptContext, p:nsc.Interpreter) {
    if (ctx.isInstanceOf[ScalaScriptContext])
       p.bind ("ctx", classOf[ScalaScriptContext].getCanonicalName, ctx)
    else
       p.bind ("ctx", classOf[ScriptContext].getCanonicalName, ctx)
    val iter = ctx.getPopulatedAttr().iterator
    while (iter.hasNext) {
      val key = iter.next
      val obj = ctx.getAttr(key);
      p.bind (key, obj.getClass.getCanonicalName, obj)
    }
  }
}

/** an interpreted scala script */
class ScriptScala (val script:String) extends RazScript {

    /** @return the statement */
    override def toString() = "scala:\n" + script

    /**
     * execute the script with the given context
     * 
     * @param c the context for the script
     */
   override def eval(ctx:ScriptContext) : AnyRef = {
      var result:AnyRef = "";

      val sctx : Option[ScalaScriptContext] = 
       if (ctx.isInstanceOf[ScalaScriptContext])
         Some(ctx.asInstanceOf[ScalaScriptContext])
       else None

      val env = sctx.map (_.env) getOrElse new scala.tools.nsc.Settings
      val p = sctx.map (_.p) getOrElse new scala.tools.nsc.Interpreter (env)         
      
      try {
         SS.bind(ctx, p)

         // this see http://lampsvn.epfl.ch/trac/scala/ticket/874 at the end, there was some work with jsr223
            
            // Now evaluate the script

            val r =  p.evalExpr[Any] (script)

            // convert to String
            result = if (r==null) "" else r.toString

            // TODO put back all variables
        } catch {
          case e:Exception => {
            razie.Log ("While processing script: " + this.script, e)
            result = "ERROR: " + e.getMessage + " : " + ctx.asInstanceOf[ScalaScriptContext].lastError
            ctx.asInstanceOf[ScalaScriptContext].lastError = ""
          }
        }
    
        result;
    }
   
    /**
     * execute the script with the given context
     * 
     * @param c the context for the script
     */
   def interactive(ctx:ScriptContext) : RazScript.SResult = {
      val sctx : Option[ScalaScriptContext] = 
       if (ctx.isInstanceOf[ScalaScriptContext])
         Some(ctx.asInstanceOf[ScalaScriptContext])
       else None

      val env = sctx.map (_.env) getOrElse new scala.tools.nsc.Settings
      val p = sctx.map (_.p) getOrElse new RaziesInterpreter (env)         
      
      try {
         SS.bind(ctx, p)

         p.eval(this, ctx)
         
        // TODO put back all variables
        } catch {
          case e:Exception => {
            razie.Log ("While processing script: " + this.script, e)
            throw e
          }
        }
    }
}

class RaziesInterpreter (s:nsc.Settings) extends nsc.Interpreter (s) {
  
  def eval (s:ScriptScala, ctx:ScriptContext) : RazScript.SResult = {
    beQuietDuring {
      interpret(s.script) match {
        case IR.Success => 
          if (razLastReq.extractionValue.isDefined) 
             RazScript.SSucc (razLastReq.extractionValue get)
          else
             RazScript.SSuccNoValue
        case IR.Error => {
           val c = RazScript.SError (razLastReq.err mkString "\n\r")
           razAccerr.clear
           c
        }
        case IR.Incomplete => RazScript.SIncomplete
     }
    }
  }
}

/** a test app */
object ScriptScalaTestApp extends Application{
    var script = "val y = 3; def f(x:int)={x+1}; val res=f(7); res";
    var js = new ScriptScala(script);
    System.out.println(js.eval(new ScriptContextImpl()));

    script = "TimeOfDay.value()";
    js = new ScriptScala(script);
    var ctx = new ScriptContextImpl();
    ctx.setAttr("TimeOfDay", new TimeOfDay(), null);
    System.out.println(js.eval(ctx));
}
