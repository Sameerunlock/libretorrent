package com.torrentbox.app.ui.settings.pages;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.torrentbox.app.databinding.FragmentAboutBinding;

public class AboutSettingsFragment extends Fragment {

    private FragmentAboutBinding binding;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentAboutBinding.inflate(inflater, container, false);

        binding.aboutText.setText(
                "TorrentBox is based on LibreTorrent.\n\n" +
                        "LibreTorrent\n" +
                        "Copyright © Yaroslav Pronin\n\n" +
                        "Licensed under the GNU General Public License v3 (GPL-3.0)\n\n" +
                        "Source code:\n" +
                        "https://github.com/Sameerunlock/libretorrent"
        );

        return binding.getRoot();
    }
}
