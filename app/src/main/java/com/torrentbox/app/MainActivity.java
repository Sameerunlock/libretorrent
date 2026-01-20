package com.torrentbox.app;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.torrentbox.app.core.RepositoryHelper;
import com.torrentbox.app.core.settings.SettingsRepository;
import com.torrentbox.app.core.utils.Utils;
import com.torrentbox.app.databinding.DialogLinkifyTextBinding;
import com.torrentbox.app.receiver.NotificationReceiver;
import com.torrentbox.app.ui.NavBarFragment;
import com.torrentbox.app.ui.NavBarFragmentDirections;
import com.torrentbox.app.ui.PermissionDeniedDialog;
import com.torrentbox.app.ui.PermissionManager;
import com.torrentbox.app.ui.base.ThemeActivity;
import com.torrentbox.app.ui.home.HomeViewModel;
import com.torrentbox.app.ui.utils.HiddenTorrentStore; // ✅ ADDED

public class MainActivity extends ThemeActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String KEY_PERMISSION_DENIED_DIALOG_REQUEST =
            TAG + "_permission_denied";

    public static final String ACTION_ADD_TORRENT_SHORTCUT =
            "org.proninyaroslav.libretorrent.ADD_TORRENT_SHORTCUT";
    public static final String ACTION_OPEN_TORRENT_DETAILS =
            "org.proninyaroslav.libretorrent.ACTION_OPEN_TORRENT_DETAILS";
    public static final String KEY_TORRENT_ID = "torrent_id";

    private NavController navController;
    private HomeViewModel viewModel;
    private PermissionManager permissionManager;
    private SettingsRepository pref;

    @NonNull
    public NavController getRootNavController() {
        return navController;
    }

    @Nullable
    public NavBarFragment findNavBarFragment(@NonNull Fragment fragment) {
        var current = fragment;
        while (current != null) {
            if (current instanceof NavBarFragment navBar) {
                return navBar;
            }
            current = current.getParentFragment();
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Utils.enableEdgeToEdge(this);

        var intentAction = getIntent().getAction();
        if (NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP.equals(intentAction)) {
            finish();
            return;
        }

        // 🔐 ALWAYS start with hidden locked
        HiddenTorrentStore.lock(this);

        getSupportFragmentManager().setFragmentResultListener(
                KEY_PERMISSION_DENIED_DIALOG_REQUEST,
                this,
                (requestKey, result) -> {
                    var res = (PermissionDeniedDialog.Result)
                            result.getSerializable(
                                    PermissionDeniedDialog.KEY_RESULT_VALUE);
                    if (res == null) return;
                    switch (res) {
                        case RETRY -> permissionManager.requestPermissions();
                        case DENIED -> permissionManager.setDoNotAskStorage(true);
                    }
                }
        );

        var provider = new ViewModelProvider(this);
        viewModel = provider.get(HomeViewModel.class);

        permissionManager = new PermissionManager(
                this,
                new PermissionManager.Callback() {
                    @Override
                    public void onStorageResult(
                            boolean isGranted,
                            boolean shouldRequestStoragePermission
                    ) {
                        if (!isGranted && shouldRequestStoragePermission) {
                            var action = NavBarFragmentDirections
                                    .actionPermissionDeniedDialog(
                                            KEY_PERMISSION_DENIED_DIALOG_REQUEST);
                            navController.navigate(action);
                        }
                    }

                    @Override
                    public void onNotificationResult(
                            boolean isGranted,
                            boolean shouldRequestNotificationPermission
                    ) {
                        permissionManager.setDoNotAskNotifications(!isGranted);
                        if (isGranted) {
                            viewModel.restartForegroundNotification();
                        }
                    }
                }
        );

        pref = RepositoryHelper.getSettingsRepository(getApplicationContext());

        setContentView(R.layout.activity_main);

        showManageAllFilesWarningDialog();

        var fm = getSupportFragmentManager();
        var navHost = (NavHostFragment)
                fm.findFragmentById(R.id.nav_host_fragment);
        if (navHost != null) {
            navController = navHost.getNavController();
        }

        var dest = navController.getCurrentDestination();
        var dialogExists = dest != null
                && dest.getId() == R.id.permissionDeniedDialog;

        if (!permissionManager.checkPermissions() && !dialogExists) {
            permissionManager.requestPermissions();
        }

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (navController == null) return;
                        if (!navController.navigateUp()) {
                            finish();
                        }
                    }
                }
        );
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 🔒 Auto-lock when app goes background
        HiddenTorrentStore.lock(this);
    }

    @Override
    protected void onDestroy() {
        if (viewModel != null) {
            viewModel.requestStopEngine();
        }
        super.onDestroy();
    }

    public void showManageAllFilesWarningDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || Utils.hasManageExternalStoragePermission(getApplicationContext())
                || !pref.showManageAllFilesWarningDialog()) {
            return;
        }

        pref.showManageAllFilesWarningDialog(false);

        var binding = DialogLinkifyTextBinding.inflate(
                LayoutInflater.from(this),
                null,
                false
        );
        binding.message.append(
                getString(R.string.manage_all_files_warning_dialog_description)
        );
        binding.message.append(Utils.getLineSeparator());
        binding.message.append(getString(R.string.project_page));

        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_warning_24px)
                .setTitle(
                        R.string.manage_all_files_warning_dialog_title)
                .setView(binding.getRoot())
                .setPositiveButton(
                        R.string.ok,
                        (dialog, which) -> dialog.dismiss()
                )
                .show();
    }
}
