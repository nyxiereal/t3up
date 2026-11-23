package meow.nyx.t3up

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.text.DateFormat
import java.util.Date

class UpdateJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            val status = try {
                Updater.update(this)
            } catch (e: Exception) {
                "Failed: ${e.message}"
            }
            saveStatus(this, status)
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val ID = 1
        private const val INTERVAL_MS = 60L * 60 * 1000

        fun prefs(ctx: Context) = ctx.getSharedPreferences("t3up", Context.MODE_PRIVATE)

        fun saveStatus(ctx: Context, status: String) {
            val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())
            prefs(ctx).edit().putString("last", "$time  $status").apply()
        }

        fun isScheduled(ctx: Context) =
            ctx.getSystemService(JobScheduler::class.java).getPendingJob(ID) != null

        fun setEnabled(ctx: Context, on: Boolean) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            if (!on) return js.cancel(ID)
            js.schedule(
                JobInfo.Builder(ID, ComponentName(ctx, UpdateJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED) // Wi-Fi only
                    .setRequiresBatteryNotLow(true)
                    .setPeriodic(INTERVAL_MS)
                    .setPersisted(true) // survives reboot
                    .build()
            )
        }
    }
}
