package com.example.project_ez_talk.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.project_ez_talk.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bottom sheet dialog for emoji/sticker picker
 */
public class EmojiPickerBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "EmojiPickerBottomSheet";
    
    private OnEmojiSelectedListener listener;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;

    // Emoji categories
    private static final List<EmojiCategory> EMOJI_CATEGORIES = Arrays.asList(
            new EmojiCategory("😊", "Smileys", Arrays.asList(
                    "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
                    "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
                    "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪",
                    "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨",
                    "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥",
                    "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕",
                    "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯",
                    "🤠", "🥳", "😎", "🤓", "🧐", "😕", "😟", "🙁",
                    "☹️", "😮", "😯", "😲", "😳", "🥺", "😦", "😧",
                    "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣",
                    "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠",
                    "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹"
            )),
            new EmojiCategory("👋", "Gestures", Arrays.asList(
                    "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤏", "✌️",
                    "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕",
                    "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜",
                    "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅",
                    "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻",
                    "👃", "🧠", "🦷", "🦴", "👀", "👁️", "👅", "👄"
            )),
            new EmojiCategory("❤️", "Hearts", Arrays.asList(
                    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
                    "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
                    "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉️", "☸️",
                    "✡️", "🔯", "🕎", "☯️", "☦️", "🛐", "⛎", "♈"
            )),
            new EmojiCategory("🐶", "Animals", Arrays.asList(
                    "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
                    "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵",
                    "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤",
                    "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗",
                    "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜",
                    "🦟", "🦗", "🕷️", "🕸️", "🦂", "🐢", "🐍", "🦎"
            )),
            new EmojiCategory("🍕", "Food", Arrays.asList(
                    "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇",
                    "🍓", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝",
                    "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️", "🌽",
                    "🥕", "🥔", "🍠", "🥐", "🥯", "🍞", "🥖", "🥨",
                    "🧀", "🥚", "🍳", "🧈", "🥞", "🧇", "🥓", "🥩",
                    "🍗", "🍖", "🌭", "🍔", "🍟", "🍕", "🥪", "🥙"
            )),
            new EmojiCategory("⚽", "Sports", Arrays.asList(
                    "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉",
                    "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
                    "🏏", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊",
                    "🥋", "🎽", "🛹", "🛼", "🛷", "⛸️", "🥌", "🎿",
                    "⛷️", "🏂", "🪂", "🏋️", "🤼", "🤸", "🤾", "🏌️"
            )),
            new EmojiCategory("🚗", "Travel", Arrays.asList(
                    "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑",
                    "🚒", "🚐", "🚚", "🚛", "🚜", "🦯", "🦽", "🦼",
                    "🛴", "🚲", "🛵", "🏍️", "🛺", "🚨", "🚔", "🚍",
                    "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋", "🚞",
                    "🚝", "🚄", "🚅", "🚈", "🚂", "🚆", "🚇", "🚊"
            )),
            new EmojiCategory("⭐", "Objects", Arrays.asList(
                    "⭐", "🌟", "✨", "⚡", "🔥", "💥", "💫", "💦",
                    "💨", "🌈", "☀️", "🌤️", "⛅", "🌥️", "☁️", "🌦️",
                    "🌧️", "⛈️", "🌩️", "🌨️", "❄️", "☃️", "⛄", "🌬️",
                    "💨", "🌪️", "🌫️", "🌊", "💧", "💦", "🎃", "🎄",
                    "🎆", "🎇", "🧨", "✨", "🎈", "🎉", "🎊", "🎋"
            ))
    );

    public interface OnEmojiSelectedListener {
        void onEmojiSelected(String emoji);
    }

    public static EmojiPickerBottomSheet newInstance(OnEmojiSelectedListener listener) {
        EmojiPickerBottomSheet fragment = new EmojiPickerBottomSheet();
        fragment.listener = listener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_emoji_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewPager = view.findViewById(R.id.viewPager);
        tabLayout = view.findViewById(R.id.tabLayout);

        setupViewPager();
    }

    private void setupViewPager() {
        EmojiPagerAdapter adapter = new EmojiPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(EMOJI_CATEGORIES.get(position).icon);
        }).attach();
    }

    /**
     * ViewPager adapter for emoji categories
     */
    private class EmojiPagerAdapter extends FragmentStateAdapter {

        public EmojiPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return EmojiGridFragment.newInstance(
                    EMOJI_CATEGORIES.get(position).emojis,
                    emoji -> {
                        if (listener != null) {
                            listener.onEmojiSelected(emoji);
                        }
                        dismiss();
                    }
            );
        }

        @Override
        public int getItemCount() {
            return EMOJI_CATEGORIES.size();
        }
    }

    /**
     * Emoji category data class
     */
    private static class EmojiCategory {
        String icon;
        String name;
        List<String> emojis;

        EmojiCategory(String icon, String name, List<String> emojis) {
            this.icon = icon;
            this.name = name;
            this.emojis = emojis;
        }
    }

    /**
     * Fragment for displaying emoji grid
     */
    public static class EmojiGridFragment extends Fragment {
        private static final String ARG_EMOJIS = "emojis";
        private List<String> emojis;
        private OnEmojiClickListener listener;

        public interface OnEmojiClickListener {
            void onEmojiClick(String emoji);
        }

        public static EmojiGridFragment newInstance(List<String> emojis, OnEmojiClickListener listener) {
            EmojiGridFragment fragment = new EmojiGridFragment();
            fragment.listener = listener;
            Bundle args = new Bundle();
            args.putStringArrayList(ARG_EMOJIS, new ArrayList<>(emojis));
            fragment.setArguments(args);
            return fragment;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.item_emoji_grid, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            if (getArguments() != null) {
                emojis = getArguments().getStringArrayList(ARG_EMOJIS);
            }

            androidx.recyclerview.widget.RecyclerView recyclerView = view.findViewById(R.id.recyclerViewEmoji);
            recyclerView.setAdapter(new EmojiAdapter(emojis, listener));
        }
    }

    /**
     * RecyclerView adapter for emojis
     */
    private static class EmojiAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder> {
        private final List<String> emojis;
        private final EmojiGridFragment.OnEmojiClickListener listener;

        EmojiAdapter(List<String> emojis, EmojiGridFragment.OnEmojiClickListener listener) {
            this.emojis = emojis;
            this.listener = listener;
        }

        @NonNull
        @Override
        public EmojiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_emoji, parent, false);
            return new EmojiViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull EmojiViewHolder holder, int position) {
            String emoji = emojis.get(position);
            holder.bind(emoji, listener);
        }

        @Override
        public int getItemCount() {
            return emojis != null ? emojis.size() : 0;
        }

        static class EmojiViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            private final android.widget.TextView tvEmoji;

            EmojiViewHolder(@NonNull View itemView) {
                super(itemView);
                tvEmoji = itemView.findViewById(R.id.tvEmoji);
            }

            void bind(String emoji, EmojiGridFragment.OnEmojiClickListener listener) {
                tvEmoji.setText(emoji);
                tvEmoji.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onEmojiClick(emoji);
                    }
                });
            }
        }
    }
}
