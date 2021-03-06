package knf.kuma.directory

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

class DirPagerAdapterOnline(fm: FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    private val animes = DirectoryPageFragmentOnline[DirectoryPageFragmentMaterial.DirType.ANIMES.value]
    private val ovas = DirectoryPageFragmentOnline[DirectoryPageFragmentMaterial.DirType.OVAS.value]
    private val movies = DirectoryPageFragmentOnline[DirectoryPageFragmentMaterial.DirType.MOVIES.value]

    override fun getPageTitle(position: Int): CharSequence? {
        return when (position) {
            1 -> "OVA"
            2 -> "PELICULA"
            else -> "ANIME"
        }
    }

    fun onChangeOrder() {

    }

    override fun getItem(position: Int): Fragment {
        return when (position) {
            1 -> ovas
            2 -> movies
            else -> animes
        }
    }

    override fun getCount(): Int {
        return 3
    }
}
