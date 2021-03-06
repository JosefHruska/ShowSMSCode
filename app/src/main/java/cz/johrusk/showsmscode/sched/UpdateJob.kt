package cz.johrusk.showsmscode.sched

import android.content.Context
import android.os.AsyncTask
import android.util.Log
import com.evernote.android.job.Job
import cz.johrusk.showsmscode.core.App
import es.dmoral.prefs.Prefs
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.warn
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.URL

/**
 * Class which handle scheduled jobs
 *
 * @author Josef Hruska (pepa.hruska@gmail.com)
 */

class UpdateJob : com.evernote.android.job.Job(), AnkoLogger {


    companion object {
        val TAG = "job_demo_tag"
        val TAG_ONSTART = "job_onstart_tag"
    }

    override fun onRunJob(params: Job.Params): Job.Result {
        if (params.tag == TAG || params.tag == TAG_ONSTART) {
            val updateTask: UpdateTask = UpdateTask(App.get())
            updateTask.execute("0")
            warn("JOB STARTED - ONSTART_JOB")
            return Job.Result.SUCCESS
        }
        return Job.Result.FAILURE
    }


}

internal class UpdateTask(private val c: Context) : AsyncTask<String, Void, Array<String>>(), AnkoLogger {

    @Throws(IOException::class)
    private fun writeToFile(data: String, name: String) {
        var file: String? = null
        if (name.equals("SMS")) {
            debug("Saving new SMS.json....")
            file = "sms.txt"
        } else if (name.equals("VER")) {
            debug("Saving new version.json...." + data)
            file = "version.txt"
            try {
                val JSONob = JSONObject(data)
                debug(JSONob)
                val updateContent = JSONob.getString("news")
                debug("NEWS is : " + updateContent)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        val outputStreamWriter = OutputStreamWriter(App.get().openFileOutput(file, Context.MODE_PRIVATE))
        outputStreamWriter.write(data)
        outputStreamWriter.close()
        if (name == "VER") {
        }
    }

    fun loadJSONFromAsset(): String? {
        var json: String? = null
        var  inStream: InputStream? = null
        try {
            val inStream = App.get().assets.open("version.json") // Todo java.lang.NullPointerException: Attempt to invoke virtual method 'int java.lang.String.length()' on a null object reference
            val size = inStream.available()
            val buffer = ByteArray(size)
            inStream.read(buffer)
            json = String(buffer, Charsets.UTF_8)
            warn("JsonString from assets look like this: $json")
            Log.d("LOG","JsonString from assets look like this: $json")
        } catch (ex: IOException) {
            return null
        }
        finally {
            inStream?.close()
        }

        debug("loadJSONFromAssets returns: $json")
        return json
    }

    @Throws(JSONException::class)
    fun localCheckVersion(): Int {

        val str: String = readFromFile("version.txt")
        debug("version string : $str")
        val offlineVer = JSONObject(str).getInt("version")
        debug("offline version is $offlineVer")

        return offlineVer
    }


    @Throws(JSONException::class)
    fun readFromFile(file: String): String {
        var ret = ""
        var inputStream:InputStream? = null
        try {
            inputStream = App.get().openFileInput(file)

            if (inputStream != null) {
                val inputStreamReader = InputStreamReader(inputStream)
                val bufferedReader = BufferedReader(inputStreamReader)
                val stringBuilder = StringBuilder()
                bufferedReader.forEachLine {stringBuilder.append(it)}

                ret = stringBuilder.toString()
            }
        } catch (e: FileNotFoundException) {
            error("File was not found")
        } catch (e: IOException) {
        }
        finally {
            inputStream?.close()
        }

        debug("readFormFile return: $ret")
        return ret
    }

    @Throws(JSONException::class)
    fun firstTimeCheckVersion(onlineVerStr: String): Boolean? {
        val SMS_FILE = "/sms.txt"
        val VERSION_FILE = "/version.txt"
        val INTERNAL_PATH_SMS = App.get().filesDir.path + SMS_FILE
        val INTERNAL_PATH_VERSION = App.get().filesDir.path + VERSION_FILE

        val version_file = File(INTERNAL_PATH_VERSION)
        val sms_file = File(INTERNAL_PATH_SMS)
        debug("Path of smsJSON: ${sms_file.absolutePath}")
        debug("Path of versionJSON:  ${version_file.absolutePath}")

        if (sms_file.exists() && version_file.exists()) {
            warn("sms.txt and version.txt exists in the internal storage")
            val localVer = localCheckVersion()
            val onlineVer = JSONObject(onlineVerStr).getInt("version")
            debug("Online Ver: " + onlineVer)

            if (onlineVer == localVer) {
               debug("The online version in internal storage is equal to online version")
                return true
            } else {
                debug("Online version in internal storage is older than online version")
                val replaceSms = UpdateTask(App.get())
                replaceSms.execute("1")
            }
        } else {
            val localVer: Int
            val onlineVer: Int
            val locObj: JSONObject
            val Json = JSONObject(onlineVerStr)

            onlineVer = Json.getInt("version")
            warn("Online version is: " + onlineVer.toString())
            val str = loadJSONFromAsset()
            locObj = JSONObject(str)
            localVer = locObj.getInt("version")
            warn("Local version is: " + localVer.toString())
            Prefs.with(c).writeInt("DBVersion", localVer)

            if (localVer == onlineVer) {
                warn("Version of JSON in assets is same as the online version")
                return true
            } else {
                warn("Version of JSON in assets is older than the online version")
                val updateSms = UpdateTask(App.get())
                updateSms.execute("1")
                warn("Local version in assets wo'nt be used anymore. Online version will be stored in the internal storage and will be used instead ")
            }
        }
        return true
    }

    override fun doInBackground(vararg params: String): Array<String> {
        var JsonStr: String? = null

        val par = Integer.valueOf(params[0])!! //all update tasks have to have this parameter as Integer in order to be execute.
        val results = arrayOfNulls<String>(2)
        var dUrl: String? = null

        val VERSION_URL = "https://rawgit.com/JosefHruska/ShowSMSCode/master/app/src/main/assets/version.json"
        val SMS_URL = "https://rawgit.com/JosefHruska/ShowSMSCode/master/app/src/main/assets/sms.json"

        if (par == 0 || par == 2) {
            dUrl = VERSION_URL
        } else if (par == 1) {
            dUrl = SMS_URL
        }
        JsonStr = URL(dUrl).readText()
        warn("Downloaded json string: $JsonStr")

        if (JsonStr != null) {
            results[0] = JsonStr
            results[1] = par.toString()
        } else {
            warn("Probably connection problem")
            this.cancel(true)
        }
        return results as Array<String> // It cant be null, otherwise this task would be cancelled before this statement
     }

    override fun onPostExecute(result: Array<String>) {

        if (result[1] != null) {
            when (result[1]) {
                "0" -> try {
                    firstTimeCheckVersion(result[0])
                } catch (e: JSONException) {
                }
                "1" -> try {
                    debug("New version.json was downloaded")
                    writeToFile(result[0], "SMS")
                    val updateVersion = UpdateTask(App.get())
                    updateVersion.execute("2")
                } catch (e: IOException) {
                }
                "2" -> try {
                    writeToFile(result[0], "VER")
                } catch (e: IOException) {
                }

            }
        } else {
            warn("Download Failed")
        }
    }
}

