const cheerio =
  require("cheerio");

const { fetch } =
  require("undici");

const BASE =
  "https://thefzmovies.com";

function clean(value) {
  return String(value || "")
    .replace(/<[^>]+>/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&#8211;|&#8212;/g, "-")
    .replace(/&#8217;/g, "'")
    .replace(/\s+/g, " ")
    .trim();
}

function detectType(post) {
  const classes =
    Array.isArray(post.class_list)
      ? post.class_list.join(" ")
      : "";

  const title =
    clean(post.title?.rendered);

  if (
    /tv-series|series|korean-series|sa-series/i.test(
      classes
    ) ||
    /\bS\d{1,3}\b/i.test(
      title
    )
  ) {
    return "series";
  }

  return "movie";
}

function poster(post) {
  return (
    post.meta?.fifu_image_url ||
    post._embedded?.["wp:featuredmedia"]?.[0]
      ?.source_url ||
    post.aioseo_head_json?.schema?.["@graph"]
      ?.find(x => x.image?.url)
      ?.image?.url ||
    null
  );
}

function mapPost(post) {
  return {
    provider: "fzmovies",
    fzId: post.id,
    id: String(post.id),
    title:
      clean(post.title?.rendered) ||
      "Untitled",
    cleanTitle:
      clean(post.title?.rendered) ||
      "Untitled",
    type:
      detectType(post),
    url:
      post.link,
    image:
      poster(post),
    year:
      Number(
        (
          clean(
            post.title?.rendered
          ).match(
            /\b(19|20)\d{2}\b/
          ) || []
        )[0]
      ) || null
  };
}

async function wp(
  path,
  params = {}
) {
  const url =
    new URL(
      `/wp-json/wp/v2/${path}`,
      BASE
    );

  for (
    const [key, value]
    of Object.entries(params)
  ) {
    if (
      value !== undefined &&
      value !== null
    ) {
      url.searchParams.set(
        key,
        String(value)
      );
    }
  }

  const response =
    await fetch(url, {
      headers: {
        "user-agent":
          "TheNkiri/3.0 Android"
      }
    });

  if (!response.ok) {
    throw new Error(
      `FZMovies API HTTP ${response.status}`
    );
  }

  return response.json();
}

async function searchFzMovies(
  query,
  limit = 20
) {
  const posts =
    await wp(
      "posts",
      {
        search: query,
        per_page:
          Math.min(
            50,
            Math.max(
              1,
              Number(limit) || 20
            )
          ),
        _embed: 1
      }
    );

  return posts.map(mapPost);
}

async function latestFzMovies(
  options = {}
) {
  const params = {
    per_page:
      Math.min(
        50,
        Math.max(
          1,
          Number(
            options.limit
          ) || 20
        )
      ),
    _embed: 1
  };

  if (options.category) {
    params.categories =
      options.category;
  }

  const posts =
    await wp(
      "posts",
      params
    );

  return posts.map(mapPost);
}

function parseLinks(html) {
  const $ =
    cheerio.load(
      html || ""
    );

  const links = [];

  $("a[href]").each(
    (_, el) => {
      const href =
        $(el).attr(
          "href"
        );

      if (!href) return;

      const label =
        clean(
          $(el).text()
        );

      if (
        !/download|episode|server/i.test(
          label
        ) &&
        !/\.(mkv|mp4)(?:$|\?)/i.test(
          href
        )
      ) {
        return;
      }

      links.push({
        label:
          label ||
          "Download",
        url:
          new URL(
            href,
            BASE
          ).href
      });
    }
  );

  return links;
}

async function getFzMovie(
  id
) {
  const post =
    await wp(
      `posts/${id}`,
      {
        _embed: 1
      }
    );

  const item =
    mapPost(post);

  const $ =
    cheerio.load(
      post.content?.rendered ||
      ""
    );

  let synopsis = "";

  $("p").each(
    (_, element) => {
      const text =
        clean(
          $(element).text()
        );

      if (
        !synopsis &&
        text &&
        !/^episode\b/i.test(
          text
        ) &&
        !/^download$/i.test(
          text
        )
      ) {
        synopsis = text;
      }
    }
  );

  const links =
    parseLinks(
      post.content?.rendered
    );

  const episodes =
    new Map();

  for (
    const link
    of links
  ) {
    const match =
      link.label.match(
        /episode\s*(\d+)/i
      );

    if (!match)
      continue;

    const number =
      Number(match[1]);

    if (
      !episodes.has(
        number
      )
    ) {
      episodes.set(
        number,
        []
      );
    }

    episodes
      .get(number)
      .push({
        label:
          link.label,
        url:
          link.url
      });
  }

  return {
    ...item,
    description:
      synopsis,
    downloadLinks:
      links.filter(
        x =>
          !/episode\s*\d+/i.test(
            x.label
          )
      ),
    episodes:
      [...episodes.entries()]
        .sort(
          (a, b) =>
            a[0] - b[0]
        )
        .map(
          ([episode, sources]) => ({
            season:
              Number(
                (
                  item.title.match(
                    /\bS0?(\d+)/i
                  ) || []
                )[1]
              ) || 1,
            episode,
            label:
              `Episode ${episode}`,
            sources
          })
        )
  };
}

module.exports = {
  searchFzMovies,
  latestFzMovies,
  getFzMovie,
  mapPost
};
