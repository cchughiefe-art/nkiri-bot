package com.nkiridown.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

class CompatibilityInstallReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        when (
            intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
            )
        ) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmation =
                    intent.getParcelableExtra<Intent>(
                        Intent.EXTRA_INTENT
                    )

                confirmation
                    ?.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                    ?.let(
                        context::startActivity
                    )
            }

            PackageInstaller.STATUS_SUCCESS -> {
                val pending =
                    CompatibilityModuleManager
                        .readPending(context)

                if (pending != null) {
                    CompatibilityModuleManager
                        .launch(
                            context,
                            pending
                        )

                    CompatibilityModuleManager
                        .clearPending(context)
                }
            }

            else -> {
                val message =
                    intent.getStringExtra(
                        PackageInstaller.EXTRA_STATUS_MESSAGE
                    )
                        ?: "Playback support could not be installed."

                Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
