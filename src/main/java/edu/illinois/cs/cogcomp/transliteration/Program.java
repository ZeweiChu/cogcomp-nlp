using System;
using System.Collections.Generic;
using System.Text;
using WAO;
using Pasternack.Collections.Generic;
using System.IO;
using System.Text.RegularExpressions;
using BetterStreams;
using System.Globalization;
using Pasternack.Utility;
using Pasternack.Collections.Generic.Specialized;
using Pasternack;

namespace SPTransliteration
{
    internal enum WeightingMode
    {
        None, FindWeighted, SuperficiallyWeighted, CountWeighted, MaxAlignment, MaxAlignmentWeighted
    }

    internal class Program
    {
        static void MakeAliasTable(string wikiFile, string redirectTableFile, string tableFile, string tableKeyValueFile)
        {
            ICSharpCode.SharpZipLib.BZip2.BZip2InputStream bzipped =
                new ICSharpCode.SharpZipLib.BZip2.BZip2InputStream(File.OpenRead(wikiFile));
            WikiXMLReader reader = new WikiXMLReader(bzipped);

            StreamReader redirectReader = new StreamReader(redirectTableFile);
            WikiRedirectTable redirectTable = new WikiRedirectTable(redirectReader);
            redirectReader.Close();

            List<string> disambigs = new List<string>();
            WikiNamespace templateNS = new WikiNamespace("Template",10);
            foreach (WikiNamespace ns in reader.WikiInfo.Namespaces)            
                if (ns.Key == 10)
                {
                    templateNS = ns;
                    break;
                }

            string disambigMain = redirectTable.Redirect(templateNS.Name + ":Disambig");
            disambigs.Add(disambigMain);
            Dictionary<string,List<string>> inverted = redirectTable.InvertedTable;
            if (inverted.ContainsKey(disambigMain))
                foreach (string template in inverted[disambigMain])
                    disambigs.Add(template);

            Dictionary<string,List<WikiAlias>> result = WikiTransliteration.MakeAliasTable(reader, disambigs);

            StreamDictionary<string,List<WikiAlias>> table = new StreamDictionary<string,List<WikiAlias>>(
                result.Count*4,0.5,tableFile,null,tableKeyValueFile,null,null,null,null,null);


            table.AddDictionary(result);

            //foreach (KeyValuePair<string, List<WikiAlias>> pair in result)
            //{
            //    table.Add(pair);
            //}

            table.Close();
        }

        static WikiRedirectTable ReadRedirectTable(string filename)
        {
            StreamReader reader = new StreamReader(filename);
            try
            {
                return new WikiRedirectTable(reader);
            }
            finally
            {
                reader.Close();
            }
        }

        static BetterBufferedStream OpenBBS(string filename, int bufferSize)
        {
            return new BetterBufferedStream(File.Open(filename, FileMode.Open), bufferSize);
        }

        static FileStream OpenFS(string filename, int bufferSize, bool random)
        {
            return new FileStream(filename, FileMode.Open, FileAccess.ReadWrite, FileShare.Read, bufferSize, random ? FileOptions.RandomAccess : FileOptions.None);
        }

        //static void MakeTranslationTable(string tableFile, string tableKeyValueFile, string sourceLanguageCode, string sourceAliasTableFile, string sourceAliasTableKeyValueFile, string sourceRedirectFile, string targetLanguageCode, string targetAliasTableFile, string targetAliasTableKeyValueFile, string targetRedirectFile)
        //{
        //    StreamDictionary<string,List<WikiAlias>> sourceAliasTable = new StreamDictionary<string,List<WikiAlias>>(
        //        5, 0.5, OpenBBS(sourceAliasTableFile, 10000), null, OpenFS(sourceAliasTableKeyValueFile,1024,true), null, null, null, null, null);   
        //    StreamDictionary<string,List<WikiAlias>> targetAliasTable = new StreamDictionary<string,List<WikiAlias>>(
        //        5, 0.5, OpenBBS(targetAliasTableFile, 10000), null, OpenFS(targetAliasTableKeyValueFile,1024,true), null, null, null, null, null);

        //    WikiTransliteration.MakeTranslationTable(sourceLanguageCode, sourceAliasTable, ReadRedirectTable(sourceRedirectFile), targetLanguageCode, targetAliasTable, ReadRedirectTable(targetRedirectFile));
        //}


        static void MakeTranslationMap(string sourceLanguageCode, string sourceAliasTableFile, string sourceAliasTableKeyValueFile, string sourceRedirectFile, string targetLanguageCode, string targetAliasTableFile, string targetAliasTableKeyValueFile, string targetRedirectFile, string translationMapFile)
        {
            StreamDictionary<string, List<WikiAlias>> sourceAliasTable = new StreamDictionary<string, List<WikiAlias>>(
                5, 0.5, OpenBBS(sourceAliasTableFile, 1000000), null, OpenBBS(sourceAliasTableKeyValueFile, 1000000), null, null, null, null, null);
            StreamDictionary<string, List<WikiAlias>> targetAliasTable = new StreamDictionary<string, List<WikiAlias>>(
                5, 0.5, OpenBBS(targetAliasTableFile, 1000000), null, OpenBBS(targetAliasTableKeyValueFile, 1000000), null, null, null, null, null);

            Dictionary<Pasternack.Utility.Pair<string, string>, int> weights;
            Map<string,string> map = WikiTransliteration.MakeTranslationMap(sourceLanguageCode, sourceAliasTable, ReadRedirectTable(sourceRedirectFile), targetLanguageCode, targetAliasTable, ReadRedirectTable(targetRedirectFile), out weights);

            System.Runtime.Serialization.Formatters.Binary.BinaryFormatter bf = new System.Runtime.Serialization.Formatters.Binary.BinaryFormatter();
            FileStream fs = File.Create(translationMapFile);
            bf.Serialize(fs, map);
            bf.Serialize(fs, weights);
            fs.Close();
        }

        static void MakeTranslationMap2(string sourceLanguageCode, string sourceAliasTableFile, string sourceAliasTableKeyValueFile, string sourceRedirectFile, string targetLanguageCode, string targetAliasTableFile, string targetAliasTableKeyValueFile, string targetRedirectFile, string translationMapFile)
        {
            StreamDictionary<string, List<WikiAlias>> sourceAliasTable = new StreamDictionary<string, List<WikiAlias>>(
                5, 0.5, OpenBBS(sourceAliasTableFile, 1000000), null, OpenBBS(sourceAliasTableKeyValueFile, 1000000), null, null, null, null, null);
            StreamDictionary<string, List<WikiAlias>> targetAliasTable = new StreamDictionary<string, List<WikiAlias>>(
                5, 0.5, OpenBBS(targetAliasTableFile, 1000000), null, OpenBBS(targetAliasTableKeyValueFile, 1000000), null, null, null, null, null);

            Dictionary<Pasternack.Utility.Pair<string, string>, WordAlignment> weights;
            Dictionary<string, bool> personTable;
            Map<string, string> map = WikiTransliteration.MakeTranslationMap2(sourceLanguageCode, sourceAliasTable, ReadRedirectTable(sourceRedirectFile), targetLanguageCode, targetAliasTable, ReadRedirectTable(targetRedirectFile), out weights, null, null, false);

            System.Runtime.Serialization.Formatters.Binary.BinaryFormatter bf = new System.Runtime.Serialization.Formatters.Binary.BinaryFormatter();
            FileStream fs = File.Create(translationMapFile);
            bf.Serialize(fs, map);
            bf.Serialize(fs, weights);
            fs.Close();
        }

        static void MakeTrainingList(string sourceLanguageCode, string sourceAliasTableFile, string sourceAliasTableKeyValueFile, string sourceCategoryGraphFile, string sourcePersondataFile, string targetLanguageCode, string targetAliasTableFile, string targetAliasTableKeyValueFile, string listFile, bool peopleOnly)
        {
            FileStream fs = File.OpenRead(sourceCategoryGraphFile);
            WikiCategoryGraph graph = (peopleOnly ? WikiCategoryGraph.ReadFromStream(fs) : null);
            fs.Close();

            Dictionary<string, bool> persondataTitles=null;

            if (peopleOnly)
            {
                persondataTitles = new Dictionary<string, bool>();
                foreach (string line in File.ReadAllLines(sourcePersondataFile)) persondataTitles[line.ToLower()] = true;
            }
            
            StreamDictionary<string, List<WikiAlias>> sourceAliasTable = new StreamDictionary<string, List<WikiAlias>>(
                5, 0.5, OpenBBS(sourceAliasTableFile, 1000000), null, OpenBBS(sourceAliasTableKeyValueFile, 1000000), null, null, null, null, null);                        

            StreamDictionary<string, List<WikiAlias>> targetAliasTable = new StreamDictionary<string, List<WikiAlias>>(
                5, 0.5, OpenBBS(targetAliasTableFile, 1000000), null, OpenBBS(targetAliasTableKeyValueFile, 1000000), null, null, null, null, null);

            Dictionary<Pasternack.Utility.Pair<string, string>, WordAlignment> weights;
            
            Map<string, string> map = WikiTransliteration.MakeTranslationMap2(sourceLanguageCode, sourceAliasTable, null, targetLanguageCode, targetAliasTable, null, out weights, graph, persondataTitles, targetLanguageCode == "zh");

            targetAliasTable.Close();
            sourceAliasTable.Close();

            StreamWriter writer = new StreamWriter(listFile, false);
            foreach (KeyValuePair<Pasternack.Utility.Pair<string, string>, WordAlignment> pair in weights)
            {
                //if (!personTable.ContainsKey(pair.Key.x)) continue; //not a person--ignore
                //bool person = false;
                //foreach (WikiAlias alias in sourceAliasTable[pair.Key.x])
                //    if (alias.type == AliasType.Link && alias.alias.StartsWith("category:",StringComparison.OrdinalIgnoreCase) && (alias.alias.Contains("births") || alias.alias.Contains("deaths")))
                //    {
                //        person=true;
                //        break;
                //    }

                //if (!person) continue;

                writer.WriteLine(pair.Key.x + "\t" + pair.Key.y + "\t" + pair.Value.ToString());
            }

            writer.Close();
        }

        static void FilterTrainingListLoose(string sourceListFile, string filteredListFile, bool removePureLatin, double minRatio, double minScore)
        {
            string[] lines = File.ReadAllLines(sourceListFile);

            Map<string, string> pairMap = new Map<string, string>();
            SparseDoubleVector<Pair<string, string>> scores = new SparseDoubleVector<Pair<string, string>>();

            foreach (string line in lines)
            {
                string[] parts = line.Split('\t');
                pairMap.Add(parts[0], parts[1]);
                WordAlignment wa = new WordAlignment(parts[2]);
                double score = wa.oneToOne * 10 + wa.equalNumber * 5 + wa.unequalNumber;
                scores[new Pair<string, string>(parts[0], parts[1])] = score;
            }            

            bool progress = true;

            StreamWriter writer = new StreamWriter(filteredListFile);
            while (progress)
            {
                List<Pair<string, string>> pairs = new List<Pair<string, string>>();
                List<double> ratios = new List<double>();

                foreach (KeyValuePair<string, string> pair in pairMap)
                {
                    double ratio = double.PositiveInfinity;
                    double pairScore = scores[pair];
                    if (pairScore < 15) continue;
                    bool goodPair = true;

                    foreach (KeyValuePair<string, string> oPair in JoinEnumerable.Join<KeyValuePair<string, string>>(pairMap.GetPairsForKey(pair.Key), pairMap.GetPairsForValue(pair.Value)))
                    {
                        if (oPair.Key == pair.Key && oPair.Value == pair.Value) continue; //this is the example we're currently looking at
                        double oScore = scores[oPair];
                        ratio = Math.Min(ratio, pairScore / oScore);
                        if (ratio < minRatio) break;
                    }

                    if (ratio < minRatio) continue; //no good
                    pairs.Add(pair);
                    ratios.Add(ratio);
                }

                progress = pairs.Count > 0;

                foreach (Pair<string, string> pair in pairs)
                {
                    pairMap.Remove(pair);
                    scores.Remove(pair);
                }
                Pair<string, string>[] pairArray = pairs.ToArray();
                double[] ratioArray = ratios.ToArray();

                Array.Sort<double, Pair<string, string>>(ratioArray, pairArray);
                
                for (int i = pairArray.Length - 1; i >= 0; i--)
                    if (removePureLatin && IsLatin(pairArray[i].y)) continue; else writer.WriteLine(pairArray[i].x + "\t" + pairArray[i].y);

            }

            writer.Close();
        }

        static void FilterTrainingList(string sourceListFile, string filteredListFile, bool removePureLatin)
        {
            string[] lines = File.ReadAllLines(sourceListFile);

            Map<string, string> pairMap = new Map<string, string>();
            SparseDoubleVector<Pair<string, string>> scores = new SparseDoubleVector<Pair<string, string>>();

            foreach (string line in lines)
            {
                string[] parts = line.Split('\t');
                pairMap.Add(parts[0], parts[1]);
                WordAlignment wa = new WordAlignment(parts[2]);
                double score = wa.oneToOne * 10 + wa.equalNumber * 5 + wa.unequalNumber;
                scores[new Pair<string, string>(parts[0], parts[1])] = score;
            }

            List<Pair<string, string>> pairs = new List<Pair<string, string>>();
            List<double> ratios = new List<double>();

            foreach (KeyValuePair<string, string> pair in pairMap)
            {
                double ratio = double.PositiveInfinity;
                double pairScore = scores[pair];
                if (pairScore < 15) continue;
                bool goodPair = true;

                foreach (KeyValuePair<string, string> oPair in JoinEnumerable.Join<KeyValuePair<string, string>>(pairMap.GetPairsForKey(pair.Key), pairMap.GetPairsForValue(pair.Value)))
                {
                    if (oPair.Key == pair.Key && oPair.Value == pair.Value) continue; //this is the example we're currently looking at
                    double oScore = scores[oPair];
                    ratio = Math.Min(ratio, pairScore / oScore);
                    if (ratio < 3) break;
                }

                if (ratio < 3) continue; //no good
                pairs.Add(pair);
                ratios.Add(ratio);                
            }

            Pair<string, string>[] pairArray = pairs.ToArray();
            double[] ratioArray = ratios.ToArray();

            Array.Sort<double, Pair<string, string>>(ratioArray, pairArray);

            StreamWriter writer = new StreamWriter(filteredListFile);
            for (int i = pairArray.Length - 1; i >= 0; i--)
                if (removePureLatin && IsLatin(pairArray[i].y)) continue; else writer.WriteLine(pairArray[i].x + "\t" + pairArray[i].y);

            writer.Close();
        }

        static Regex latinRegex = new Regex("[0-9a-zA-Z]+", RegexOptions.Compiled);
        static bool IsLatin(string word)
        {
            return latinRegex.IsMatch(word);
        }

        static void TestForMissedTranslations(string sourceAliasTableFile, string sourceAliasTableKeyValueFile, string targetLanguageCode)
        {
            StreamDictionary<string, List<WikiAlias>> sourceAliasTable = new StreamDictionary<string, List<WikiAlias>>(
                5, 0.5, OpenBBS(sourceAliasTableFile, 1000000), null, OpenBBS(sourceAliasTableKeyValueFile, 1000000), null, null, null, null, null);

            targetLanguageCode += ":";
            
            int targetCount = 0;
            int totalCount = 0;
            int otherCount = 0;
            foreach (KeyValuePair<string, List<WikiAlias>> pair in sourceAliasTable)
            {                
                bool target = false;
                bool others = false;
                foreach (WikiAlias alias in pair.Value)
                {
                    if (alias.type == AliasType.Interlanguage)
                        if (alias.alias.StartsWith(targetLanguageCode, StringComparison.OrdinalIgnoreCase))
                        {
                            target = true;
                        }
                        else
                        {
                            others = true;
                        }                                        
                }

                totalCount++;
                if (!target && others) otherCount++;
                if (target) targetCount++;
            }

            Console.WriteLine("Total: " + totalCount);
            Console.WriteLine("Target: " + targetCount);
            Console.WriteLine("Others: " + otherCount);
            Console.ReadLine();
        }

        public static Map<string, string> ReadTranslationMap(string translationMapFile, out Dictionary<Pasternack.Utility.Pair<string, string>, int> weights)
        {
            System.Runtime.Serialization.Formatters.Binary.BinaryFormatter bf = new System.Runtime.Serialization.Formatters.Binary.BinaryFormatter();
            FileStream fs = File.OpenRead(translationMapFile);
            Map<string,string> map = (Map<string, string>)bf.Deserialize(fs);
            weights = (Dictionary<Pasternack.Utility.Pair<string, string>, int>)bf.Deserialize(fs);            
            fs.Close();

            return map;
        }

        public static Map<string, string> ReadTranslationMap2(string translationMapFile, out Dictionary<Pasternack.Utility.Pair<string, string>, WordAlignment> weights)
        {
            System.Runtime.Serialization.Formatters.Binary.BinaryFormatter bf = new System.Runtime.Serialization.Formatters.Binary.BinaryFormatter();
            FileStream fs = File.OpenRead(translationMapFile);
            Map<string, string> map = (Map<string, string>)bf.Deserialize(fs);
            weights = (Dictionary<Pasternack.Utility.Pair<string, string>, WordAlignment>)bf.Deserialize(fs);
            fs.Close();

            return map;
        }

        static string FindClosest(IEnumerable<string> strings, string original)
        {
            int min = int.MaxValue;
            int lengthDistance = int.MaxValue;
            string result = null;
            foreach (string str in strings)
            {
                int aD;
                int dist = WikiTransliteration.EditDistance<char>(str, original, out aD);
                if (dist < min || (dist==min && Math.Abs(str.Length-original.Length) < lengthDistance))
                {
                    lengthDistance = Math.Abs(str.Length - original.Length);
                    result = str;
                    min = dist;
                }
            }

            return result;
        }

        static List<Pair<string, double>> FindDistances(ICollection<string> strings, string original, int multiplier)
        {
            if (original.Length > 5) original = original.Substring(0, 5);
            int editLength;
            double fmultiplier = ((double)1) / multiplier;
            List<Pair<string, double>> result = new List<Pair<string, double>>(strings.Count);
            foreach (string s in strings)
                result.Add(new Pair<string, double>(s, fmultiplier * (1 + WikiTransliteration.EditDistance<char>(s.Length > 5 ? s.Substring(0,5) : s, original, out editLength) + (Math.Abs(original.Length - s.Length))/((double)100))));

            return result;
        }

        public static string StripAccent(string stIn)
        {
            string normalized = stIn.Normalize(NormalizationForm.FormD);
            StringBuilder sb = new StringBuilder();

            foreach (char c in normalized)
            {
                UnicodeCategory uc = CharUnicodeInfo.GetUnicodeCategory(c);
                if (uc != UnicodeCategory.NonSpacingMark)
                {
                    sb.Append(c);
                }
            }
            return (sb.ToString());
        } 

        static void TestAlexData()
        {
            Dictionary<Pasternack.Utility.Pair<string, string>, int> weights;
            Map<string, string> translationMap = ReadTranslationMap(@"C:\Data\WikiTransliteration\enRuTranslationMap.dat", out weights);

            Map<string, string> deaccent = new Map<string, string>();
            foreach (string key in translationMap.Keys)                
                deaccent.Add(StripAccent(key), key);


            Dictionary<string, List<string>> alexData = GetAlexData(@"C:\Users\jpaster2\Desktop\res\res\Russian\evalpairs.txt");
            List<string> alexWords = new List<string>(GetAlexWords().Keys);
            //for (int i = 0; i < alexWords.Count; i++)
            //    if (alexWords[i].Length > 5)
            //        alexWords[i] = alexWords[i].Substring(0, 5);

            int total = 0;
            int found = 0;
            int correct = 0;
            StringBuilder output = new StringBuilder();

            foreach (KeyValuePair<string, List<string>> pair in alexData)
            {
                total++;
                string english = pair.Key.ToLower();

                //if (english == "boston")
                //    Console.WriteLine("PLASMA!");

                //if (!translationMap.ContainsKey(english)) continue;
                if (!translationMap.ContainsKey(english))
                {
                    if (deaccent.ContainsKey(StripAccent(english)))
                        english = deaccent.GetValuesForKey(StripAccent(english))[0];
                    else
                    {
                        output.AppendLine("Couldn't find english word: " + english);
                        continue; //english = FindClosest(translationMap.Keys, english);
                    }
                }

                found++;

                bool correctFlag = false;

                string[] rawRussianTranslations = translationMap.GetValuesForKey(english);
                List<string> russianTranslations = new List<string>();
                foreach (string r in rawRussianTranslations)
                {
                    if (!Regex.IsMatch(r, "^[0-9a-zA-Z]+$", RegexOptions.Compiled))
                        russianTranslations.Add(r);
                }

                //Array.Sort<string>(russianTranslations, delegate(string a, string b) { return weights[new Pair<string, string>(english, b)] - weights[new Pair<string, string>(english, a)]; });
                russianTranslations.Sort(delegate(string a, string b) { return weights[new Pair<string, string>(english, b)] - weights[new Pair<string, string>(english, a)]; });

                List<Pair<string,double>> possibilities = new List<Pair<string,double>>();

                Dictionary<string, bool> closest = new Dictionary<string, bool>(21);

                for (int i = 0; i < Math.Min(20, russianTranslations.Count); i++)
                {
                    //closest[russianTranslations[i]] = true;
                    possibilities.AddRange(FindDistances(alexWords, russianTranslations[i], weights[new Pair<string, string>(english, russianTranslations[i])]));
                }

                possibilities.Sort(delegate(Pair<string, double> a, Pair<string, double> b) { return Math.Sign(a.y - b.y); });                            

                foreach (Pair<string, double> poss in possibilities)
                {
                    closest[poss.x] = true;
                    if (closest.Count >= 20) break;
                }

                foreach (string russian in pair.Value)
                {                    
                    //if (((ICollection<string>)translationMap.GetValuesForKey(english)).Contains(russian.ToLower()))
                    if (closest.ContainsKey(russian))
                    {
                        correct++;
                        correctFlag = true;
                        break;
                    }
                }

                if (!correctFlag)
                {
                    output.AppendLine("English name: " + english);
                    output.AppendLine("Should be one of: " + String.Join(", ",pair.Value.ToArray()));
                    output.Append("Is: ");
                    foreach (string s in translationMap.GetValuesForKey(english))
                    {
                        output.Append(s); output.Append(" (" + weights[new Pair<string, string>(english, s)] + "), ");
                    }

                    output.AppendLine();

                    output.AppendLine();
                    //output.AppendLine("Is: " + String.Join(", ", ));
                }
                
            }
            

            output.AppendLine();
            output.AppendLine("Total: " + total);
            output.AppendLine("Found: " + (((double)found) / total));
            output.AppendLine("Correct: " + (((double)correct) / total));
            output.AppendLine("Correct out of found: " + (((double)correct) / found));

            File.WriteAllText(@"C:\Data\Wikitransliteration\AlexResults.txt",output.ToString());
        }

        public static Dictionary<string, bool> GetAlexWords()
        {
            string[] files = Directory.GetFiles(@"C:\Data\WikiTransliteration\complete\ver.1\", "*.rus");
            Dictionary<string, bool> result = new Dictionary<string, bool>();

            foreach (string file in files)
            {
                string text = File.ReadAllText(file, Encoding.Unicode);
                string[] words = Regex.Split(text, @"[\W\d]", RegexOptions.Compiled);
                foreach (string word in words)
                    if (word.Length > 0)
                        result[word.ToLower()] = true;
            }

            return result;
        }

        static void WriteRedirectTable(string wikiFile, string redirectFile)
        {
            ICSharpCode.SharpZipLib.BZip2.BZip2InputStream bzipped =
                new ICSharpCode.SharpZipLib.BZip2.BZip2InputStream(File.OpenRead(wikiFile));
            WikiXMLReader reader = new WikiXMLReader(bzipped);

            StreamWriter writer = new StreamWriter(redirectFile);
            WikiRedirectTable.WriteRedirectPairs(reader, writer);
            writer.Close();
            reader.Close();
        }

        static void WriteWPTitles()
        {
            StreamWriter writer = new StreamWriter(@"C:\Data\WikiTransliteration\Segmentation\enWPTitles.txt");

            ICSharpCode.SharpZipLib.BZip2.BZip2InputStream bzipped =
                new ICSharpCode.SharpZipLib.BZip2.BZip2InputStream(File.OpenRead(@"C:\Users\jpaster2\Downloads\enwiki-20090530-pages-articles.xml.bz2"));
            WikiXMLReader reader = new WikiXMLReader(bzipped);
            foreach (WikiPage page in reader.Pages)
                writer.WriteLine(page.Title);

            writer.Close();
            reader.Close();
        }

        public static void WriteSegmentCounts()
        {            
            StreamReader reader = new StreamReader(@"C:\Data\WikiTransliteration\Segmentation\enWPTitles.txt");
            Dictionary<string, bool> words = new Dictionary<string, bool>();
            string line;
            while ((line = reader.ReadLine()) != null)
            {
                if (WikiNamespace.GetNamespace(line, Wikipedia.Namespaces) != Wikipedia.DefaultNS) continue;
                foreach (string word in WordSegmentation.SplitWords(line))
                    words[word.ToLower()] = true;
            }

            WordSegmentation.WriteCounts(@"C:\Data\WikiTransliteration\Segmentation\enSegCounts.txt", WordSegmentation.GetNgramCounts(words.Keys));
        }


        //public static void WriteSegmentCounts2()
        //{
        //    StreamReader reader = new StreamReader(@"C:\Data\WikiTransliteration\Segmentation\enWPTitles.txt");
        //    Dictionary<string, bool> words = new Dictionary<string, bool>();
        //    string line;
        //    while ((line = reader.ReadLine()) != null)
        //    {
        //        if (WikiNamespace.GetNamespace(line, Wikipedia.Namespaces) != Wikipedia.DefaultNS) continue;
        //        foreach (string word in WordSegmentation.SplitWords(line))
        //            words[word.ToLower()] = true;
        //    }

        //    WordSegmentation.WriteVector(@"C:\Data\WikiTransliteration\Segmentation\enSegCounts2.dat", WordSegmentation.GetSegCounts(words.Keys));
        //}

        public static void RunSegEMExperiment()
        {
            StreamReader reader = new StreamReader(@"C:\Data\WikiTransliteration\Segmentation\enWPTitles.txt");
            SparseDoubleVector<string> words = new SparseDoubleVector<string>();
            string line;
            while ((line = reader.ReadLine()) != null)
            {
                if (WikiNamespace.GetNamespace(line, Wikipedia.Namespaces) != Wikipedia.DefaultNS) continue;
                foreach (string word in WordSegmentation.SplitWords(line))
                    if (word.Length < 20) words[word.ToLower()] += 1;  //no insanely long words
            }

            WordSegmentation.Learn(words);
        }

        public static void RunCompressExperiment()
        {
            StreamReader reader = new StreamReader(@"C:\Data\WikiTransliteration\Segmentation\enWPTitles.txt");
            SparseDoubleVector<string> words = new SparseDoubleVector<string>();
            string line;
            while ((line = reader.ReadLine()) != null)
            {
                if (WikiNamespace.GetNamespace(line, Wikipedia.Namespaces) != Wikipedia.DefaultNS) continue;
                foreach (string word in WordSegmentation.SplitWords(line))
                    if (word.Length < 20) words[word.ToLower()] += 1;  //no insanely long words
            }

            words = (words / 10).Floor().Sign();
            words.RemoveRedundantElements();

            WordCompression.Compress(words);
        }

        public static void RunCompressDemo()
        {
            StreamReader reader = new StreamReader(@"C:\Data\WikiTransliteration\Segmentation\chunks.txt");
            SparseDoubleVector<string> words = new SparseDoubleVector<string>();
            string line;
            while ((line = reader.ReadLine()) != null) words.Add(line, 0.5);

            Console.WriteLine("Proceed.");

            while (true)
            {
                string word = Console.ReadLine();
                WordSegmentation.OutputSegmentations(word, 10, words);
            }
        }

        static void Main(string[] args)
        {
            Console.SetBufferSize(Console.BufferWidth, short.MaxValue - 1);

            //RussianDiscoveryTest(); return;
            ChineseDiscoveryTest(); return;
            //HebrewDiscoveryTest(); return;

            //WriteWPTitles(); return;
            //WriteSegmentCounts2(); return;
            //WordSegmentation.Interactive2(@"C:\Data\WikiTransliteration\Segmentation\enSegCounts2.dat"); return;
            //RunSegEMExperiment(); return;
            //RunCompressExperiment(); return;

            //RunCompressDemo(); return;

            //Dictionary<string, bool> alexWords = GetAlexWords();
            //Dictionary<string, List<string>> alexData = GetAlexData();

            //foreach (List<string> list in alexData.Values)
            //    for (int i = 0; i < list.Count; i++)
            //        list[i] = list[i].ToLower();

            //int found = 0;
            //foreach (KeyValuePair<string, List<string>> pair in alexData)
            //{
            //    foreach (string word in pair.Value)
            //        if (alexWords.ContainsKey(word))
            //        {
            //            found++; break;
            //        }
            //}

            //Console.WriteLine((((double)found) / alexData.Count));
            //Console.ReadLine();

            //ChaeckCoverage(@"C:\Data\WikiTransliteration\enZhTranslationMap.dat", @"C:\Data\WikiTransliteration\Task\Chinese\NEWS09_dev_EnCh_2896.xml", @"C:\Data\WikiTransliteration\Task\Chinese\NEWS09_train_EnCh_31961.xml");
            //FindSynonyms(@"C:\Data\WikiTransliteration\enRedirectTable.dat");

            //MakeAlignmentTableEM(@"C:\Data\WikiTransliteration\enRuTranslationMap.dat", @"C:\Data\WikiTransliteration\enRuAligns5.dat", 5, 0);
           
            //TestAlexAlignment(@"C:\Data\WikiTransliteration\enRuAligns5.dat", 5);
            //TestXMLDataNewStyle(@"C:\Data\WikiTransliteration\NEWS09_train_EnRu_5977.xml", @"C:\Data\WikiTransliteration\NEWS09_dev_EnRu_943.xml", @"C:\Data\WikiTransliteration\NEWS09_test_EnRu_1000.xml", 15, 15);
            
            //TestXMLData(@"C:\Data\WikiTransliteration\Task\Chinese\NEWS09_train_EnCh_31961.xml", @"C:\Data\WikiTransliteration\Task\Chinese\NEWS09_dev_EnCh_2896.xml", 15, 15);
            //TestXMLData(@"C:\Data\WikiTransliteration\NEWS09_train_EnRu_5977.xml", @"C:\Data\WikiTransliteration\NEWS09_dev_EnRu_943.xml", 15, 15);

            //TestXMLDataWithContext(@"C:\Data\WikiTransliteration\NEWS09_train_EnRu_5977.xml", @"C:\Data\WikiTransliteration\NEWS09_dev_EnRu_943.xml", 15,1,true);
            //TestXMLDataWithContext(@"C:\Data\WikiTransliteration\Task\Chinese\NEWS09_train_EnCh_31961.xml", @"C:\Data\WikiTransliteration\Task\Chinese\NEWS09_dev_EnCh_2896.xml", 15, 1, true);

            //TestXMLDataOldOldStyle(@"C:\Data\WikiTransliteration\Task\Chinese\NEWS09_train_EnCh_31961.xml", @"C:\Data\WikiTransliteration\Task\Chinese\NEWS09_dev_EnCh_2896.xml",15,15);
            //TestXMLDataOldOldStyleForTask(@"C:\Data\WikiTransliteration\Task\Chinese\NEWS09_train_EnCh_31961.xml", @"C:\Data\WikiTransliteration\Task\Chinese\NEWS09_dev_EnCh_2896.xml", @"C:\Data\WikiTransliteration\NEWS09_test_EnCh_2896.xml", 15, 15);

            




            #region Russian discovery-generation test

            //List<string> candidateList = new List<string>(File.ReadAllLines(@"C:\Users\jpaster2\Desktop\res\res\Russian\RussianWords"));
            //for (int i = 0; i < candidateList.Count; i++) candidateList[i] = candidateList[i].ToLower();
            ////candidateList.Clear();
            //Dictionary<string, List<string>> evalList = GetAlexData(@"C:\Users\jpaster2\Desktop\res\res\Russian\evalpairs.txt");//@"C:\Users\jpaster2\Desktop\res\res\Russian\evalpairs.txt");            
            //List<KeyValuePair<string, string>> trainList = GetTabDelimitedPairs(@"C:\Data\WikiTransliteration\Aliases\ruExamples.txt");            
            //List<KeyValuePair<string, string>> trainList2 = new List<KeyValuePair<string, string>>(trainList.Count);

            //Dictionary<string, bool> usedExamples = new Dictionary<string, bool>();
            //foreach (KeyValuePair<string, List<string>> pair in evalList) usedExamples[pair.Key] = true;
            ////trainList2 = TruncateList(trainList2, 2000);
            //foreach (KeyValuePair<string, string> pair in trainList) if (!usedExamples.ContainsKey(pair.Key)) trainList2.Add(pair);

            //DiscoveryGenerationTest(RemoveDuplicates(candidateList), trainList2, evalList, 15, 15);            

            #endregion

            #region Russian generation test (all on WP data)
            //List<KeyValuePair<string, string>> trainList = GetTabDelimitedPairs(@"C:\Data\WikiTransliteration\Aliases\ruExamples.txt");
            //List<KeyValuePair<string, string>> evalList = (GetRandomPartOfList(trainList, 300, 5320));
            //TestXMLData(trainList, evalList, 15, 15);            
            #endregion

            #region Russian EM-determination

            //List<string> candidateList = new List<string>(File.ReadAllLines(@"C:\Users\jpaster2\Desktop\res\res\Russian\RussianWordsShort"));
            //for (int i = 0; i < candidateList.Count; i++) candidateList[i] = candidateList[i].ToLower();
            //candidateList.Clear();
            //Dictionary<string, List<string>> evalList; //GetAlexData(@"C:\Users\jpaster2\Desktop\res\res\Russian\evalpairsShort.txt");//@"C:\Users\jpaster2\Desktop\res\res\Russian\evalpairs.txt");
            ////List<KeyValuePair<string, string>> trainList = NormalizeHebrew(GetTabDelimitedPairs(@"C:\Users\jpaster2\Desktop\res\res\Hebrew\train_EnglishHebrew.txt"));
            //List<KeyValuePair<string, string>> trainList = GetTabDelimitedPairs(@"C:\Data\WikiTransliteration\Aliases\ruExamples.txt");
            ////List<KeyValuePair<string, string>> trainList2 = RemoveVeryLong(NormalizeHebrew(GetTabDelimitedPairs(@"C:\Data\WikiTransliteration\Aliases\heExamples-Lax.txt")), 20);
            //List<KeyValuePair<string, string>> trainList2 = new List<KeyValuePair<string, string>>(trainList.Count);

            //Dictionary<string, bool> usedExamples = new Dictionary<string, bool>();
            ////foreach (KeyValuePair<string, List<string>> pair in evalList) usedExamples[pair.Key] = true;
            ////trainList2 = TruncateList(trainList2, 2000);
            //foreach (KeyValuePair<string, string> pair in trainList) if (!usedExamples.ContainsKey(pair.Key)) trainList2.Add(pair);

            //evalList = LiftPairList( GetRandomPartOfList(trainList2, trainList2.Count / 10, 34523) );

            ////DiscoveryTestDual(RemoveDuplicates(candidateList), trainList2, evalList, 15, 15);
            //DiscoveryTestDual(RemoveDuplicates(GetWords(evalList)), trainList2, evalList, 15, 15);

            #endregion

            //WriteRedirectTable(@"C:\Users\jpaster2\Downloads\ruwiki-20090603-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\Aliases\ruRedirectTable.dat");
            //MakeAliasTable(@"C:\Users\jpaster2\Downloads\ruwiki-20090603-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\Aliases\ruRedirectTable.dat", @"C:\Data\WikiTransliteration\Aliases\ruAliasTable.dat", @"C:\Data\WikiTransliteration\Aliases\ruAliasTableKeyValue.dat");

            //WriteRedirectTable(@"C:\Users\jpaster2\Downloads\hewiki-20090601-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\Aliases\heRedirectTable.dat");
            //MakeAliasTable(@"C:\Users\jpaster2\Downloads\hewiki-20090601-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\Aliases\heRedirectTable.dat", @"C:\Data\WikiTransliteration\Aliases\heAliasTable.dat", @"C:\Data\WikiTransliteration\Aliases\heAliasTableKeyValue.dat");

            //WriteRedirectTable(@"C:\Users\jpaster2\Downloads\zhwiki-20090602-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\Aliases\zhRedirectTable.dat");
            //MakeAliasTable(@"C:\Users\jpaster2\Downloads\zhwiki-20090602-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\Aliases\zhRedirectTable.dat", @"C:\Data\WikiTransliteration\Aliases\zhAliasTable.dat", @"C:\Data\WikiTransliteration\Aliases\zhAliasTableKeyValue.dat");

            //WriteRedirectTable(@"C:\Users\jpaster2\Downloads\enwiki-20090530-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\Aliases\enRedirectTable.dat");
            //MakeAliasTable(@"C:\Users\jpaster2\Downloads\enwiki-20090530-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\Aliases\enRedirectTable.dat", @"C:\Data\WikiTransliteration\Aliases\enAliasTable.dat", @"C:\Data\WikiTransliteration\Aliases\enAliasTableKeyValue.dat");


            //ICSharpCode.SharpZipLib.BZip2.BZip2InputStream bzipped =
            //    new ICSharpCode.SharpZipLib.BZip2.BZip2InputStream(File.OpenRead(@"C:\Users\jpaster2\Downloads\enwiki-20090530-pages-articles.xml.bz2"));
            //WikiXMLReader reader = new WikiXMLReader(bzipped);

            //StreamWriter writer = new StreamWriter(@"C:\Data\WikiTransliteration\Aliases\enPersondata.txt");

            //foreach (WikiPage page in reader.Pages)
            //{                
            //    WikiRevision revision = reader.NextRevision();
            //    if (revision.Text.IndexOf("{{persondata", StringComparison.OrdinalIgnoreCase) >= 0 || revision.Text.IndexOf("{{lifetime", StringComparison.OrdinalIgnoreCase) >= 0)
            //        writer.WriteLine(page.Title);
            //}

            //writer.Close();


            //WikiCategoryGraph graph = new WAO.WikiCategoryGraph(reader, null, true, true);
            //FileStream fs = File.OpenWrite(@"C:\Data\WikiTransliteration\Aliases\enCatGraph.dat");
            //graph.WriteToStream(fs);
            //fs.Close();

            //string[] targets = new string[] { "he", "ru", "zh" };
            //foreach (string target in targets)
            //    //MakeTrainingList("en", @"C:\Data\WikiTransliteration\Aliases\enAliasTable.dat", @"C:\Data\WikiTransliteration\Aliases\enAliasTableKeyValue.dat", @"C:\Data\WikiTransliteration\Aliases\enCatGraph.dat", @"C:\Data\WikiTransliteration\Aliases\enPersonData.txt", target, @"C:\Data\WikiTransliteration\Aliases\" + target + "AliasTable.dat", @"C:\Data\WikiTransliteration\Aliases\" + target + "AliasTableKeyValue.dat", @"C:\Data\WikiTransliteration\Aliases\en" + target + "TrainingList-All.txt", false);
            //    FilterTrainingListLoose(@"C:\Data\WikiTransliteration\Aliases\en" + target + "TrainingList.txt", @"C:\Data\WikiTransliteration\Aliases\" + target + "Examples-Lax.txt", true, 2, 5);

            //string target = "he";
            //MakeTrainingList("en", @"C:\Data\WikiTransliteration\Aliases\enAliasTable.dat", @"C:\Data\WikiTransliteration\Aliases\enAliasTableKeyValue.dat", @"C:\Data\WikiTransliteration\Aliases\enCatGraph.dat", @"C:\Data\WikiTransliteration\Aliases\enPersonData.txt", target, @"C:\Data\WikiTransliteration\Aliases\" + target + "AliasTable.dat", @"C:\Data\WikiTransliteration\Aliases\"+target+"AliasTableKeyValue.dat", @"C:\Data\WikiTransliteration\Aliases\en"+target+"TrainingList.txt");

            //target = "ru";
            //MakeTrainingList("en", @"C:\Data\WikiTransliteration\Aliases\enAliasTable.dat", @"C:\Data\WikiTransliteration\Aliases\enAliasTableKeyValue.dat", @"C:\Data\WikiTransliteration\Aliases\enCatGraph.dat", @"C:\Data\WikiTransliteration\Aliases\enPersonData.txt", target, @"C:\Data\WikiTransliteration\Aliases\" + target + "AliasTable.dat", @"C:\Data\WikiTransliteration\Aliases\" + target + "AliasTableKeyValue.dat", @"C:\Data\WikiTransliteration\Aliases\en" + target + "TrainingList.txt");

            //string target = "zh";
            //MakeTrainingList("en", @"C:\Data\WikiTransliteration\Aliases\enAliasTable.dat", @"C:\Data\WikiTransliteration\Aliases\enAliasTableKeyValue.dat", @"C:\Data\WikiTransliteration\Aliases\enCatGraph.dat", @"C:\Data\WikiTransliteration\Aliases\enPersonData.txt", target, @"C:\Data\WikiTransliteration\Aliases\" + target + "AliasTable.dat", @"C:\Data\WikiTransliteration\Aliases\" + target + "AliasTableKeyValue.dat", @"C:\Data\WikiTransliteration\Aliases\en" + target + "TrainingList.txt");
            //FilterTrainingList(@"C:\Data\WikiTransliteration\Aliases\en" + target + "TrainingList.txt", @"C:\Data\WikiTransliteration\Aliases\" + target + "Examples.txt");

            //rebuild....
            //MakeAliasTable(@"C:\Data\Wikidumps\ruwiki-20081228-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\ruRedirectTable.dat", @"C:\Data\WikiTransliteration\ruAliasTable.dat", @"C:\Data\WikiTransliteration\ruAliasTableKeyValue.dat");

            //WriteRedirectTable(@"C:\Data\Wikidumps\zhwiki-20090116-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\zhRedirectTable.dat");
            //MakeAliasTable(@"C:\Data\Wikidumps\zhwiki-20090116-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\zhRedirectTable.dat", @"C:\Data\WikiTransliteration\zhAliasTable.dat", @"C:\Data\WikiTransliteration\zhAliasTableKeyValue.dat");
            //MakeTranslationMap("en", @"C:\Data\WikiTransliteration\enAliasTable.dat", @"C:\Data\WikiTransliteration\enAliasTableKeyValue.dat", @"C:\Data\WikiTransliteration\enRedirectTable.dat", "zh", @"C:\Data\WikiTransliteration\zhAliasTable.dat", @"C:\Data\WikiTransliteration\zhAliasTableKeyValue.dat", @"C:\Data\WikiTransliteration\zhRedirectTable.dat", @"C:\Data\WikiTransliteration\enZhTranslationMap.dat");

            //MakeTranslationMap2("en", @"C:\Data\WikiTransliteration\enAliasTable.dat", @"C:\Data\WikiTransliteration\enAliasTableKeyValue.dat", @"C:\Data\WikiTransliteration\enRedirectTable.dat", "ru", @"C:\Data\WikiTransliteration\ruAliasTable.dat", @"C:\Data\WikiTransliteration\ruAliasTableKeyValue.dat", @"C:\Data\WikiTransliteration\ruRedirectTable.dat", @"C:\Data\WikiTransliteration\enRuTranslationMap.dat");

            //TestForMissedTranslations(@"C:\Data\WikiTransliteration\ruAliasTable.dat", @"C:\Data\WikiTransliteration\ruAliasTableKeyValue.dat","en");
            //TestAlexData();            

            //WriteRedirectTable(@"C:\Data\Wikidumps\enwiki-20090306-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\enRedirectTable.dat");
            //MakeAliasTable(@"C:\Data\Wikidumps\enwiki-20090306-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\enRedirectTable.dat", @"C:\Data\WikiTransliteration\enAliasTable.dat", @"C:\Data\WikiTransliteration\enAliasTableKeyValue.dat");
            //MakeTranslationMap(@"C:\Data\WikiTransliteration\en2ruTranslationTable.dat", @"C:\Data\WikiTransliteration\en2ruTranslationTableKeyValue.dat", "en", @"C:\Data\WikiTransliteration\enAliasTable.dat", @"C:\Data\WikiTransliteration\enAliasTableKeyValue.dat", @"C:\Data\WikiTransliteration\enRedirectTable.dat", "ru", @"C:\Data\WikiTransliteration\ruAliasTable.dat", @"C:\Data\WikiTransliteration\ruAliasTableKeyValue.dat", @"C:\Data\WikiTransliteration\ruRedirectTable.dat", @"C:\Data\WikiTransliteration\enRuTranslationMap.dat");

            //MakeAliasTable(@"C:\Data\Wikidumps\ruwiki-20081228-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\ruRedirectTable.dat", @"C:\Data\WikiTransliteration\ruAliasTable.dat", @"C:\Data\WikiTransliteration\ruAliasTableKeyValue.dat");
            //AlexTestTable();
            //MakeRawLinkTable(@"C:\Data\Wikidumps\ruwiki-20081228-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\ruRawLinks.txt");
            //MakeRawLinkTable(@"C:\Data\Wikidumps\enwiki-20081008-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\enRawLinks.txt");

            //FindOverlap();

            //MakeTable();
            //TestTable();

            //StreamWriter writer = new StreamWriter(@"C:\Data\WikiTransliteration\translationsRaw.txt");

            //GetEntities(@"C:\Data\Wikidumps\ruwiki-20081228-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\ruRedirectTable.dat", false, writer,false);
            //GetEntities(@"C:\Data\Wikidumps\enwiki-20081008-pages-articles.xml.bz2", @"C:\Data\WikiTransliteration\enRedirectTable.dat", true, writer, false);

            //writer.Close();
        }

        public static void HebrewDiscoveryTest()
        {
            #region Hebrew discovery test
            List<KeyValuePair<string, string>> wordList = NormalizeHebrew(GetTabDelimitedPairs(@"C:\Users\jpaster2\Desktop\res\res\Hebrew\evalwords.txt"));
            List<KeyValuePair<string, string>> trainList = NormalizeHebrew(GetTabDelimitedPairs(@"C:\Users\jpaster2\Desktop\res\res\Hebrew\train_EnglishHebrew.txt"));
            List<KeyValuePair<string, string>> trainList2 = NormalizeHebrew(GetTabDelimitedPairs(@"C:\Data\WikiTransliteration\Aliases\heExamples.txt"));
            //List<KeyValuePair<string, string>> trainList2 = RemoveVeryLong(NormalizeHebrew(GetTabDelimitedPairs(@"C:\Data\WikiTransliteration\Aliases\heExamples-Lax.txt")), 20);            

            Dictionary<string, bool> usedExamples = new Dictionary<string, bool>();
            foreach (KeyValuePair<string, string> pair in JoinEnumerable.Join<KeyValuePair<string, string>>(wordList, trainList)) usedExamples[pair.Key] = true;
            //trainList2 = TruncateList(trainList2, 2000);
            foreach (KeyValuePair<string, string> pair in trainList2) if (!usedExamples.ContainsKey(pair.Key)) trainList.Add(pair);

            //DiscoveryTestDual(RemoveDuplicates(GetListValues(wordList)), trainList, LiftPairList(wordList), 15, 15);
            //TestXMLData(trainList, wordList, 15, 15);


            List<string> candidateList = GetListValues(wordList);
            //wordList = GetRandomPartOfList(trainList, 50, 31);
            candidateList.AddRange(GetListValues(wordList));

            DiscoveryEM(200, RemoveDuplicates(candidateList), trainList, LiftPairList(wordList), new CSPModel(40, 0, 0, 0.000000000000001, SegMode.None, false, SmoothMode.BySum, FallbackStrategy.NotDuringTraining, EMMode.Normal,false));
            //DiscoveryEM(200, RemoveDuplicates(candidateList), trainList, LiftPairList(wordList), new CSPModel(40, 0, 0, 0, FallbackStrategy.Standard));

            //DiscoveryTestDual(RemoveDuplicates(candidateList), trainList, LiftPairList(wordList), 40, 40);
            //DiscoveryTest(RemoveDuplicates(candidateList), trainList, LiftPairList(wordList), 40, 40);
            #endregion
        }

        public static void ChineseDiscoveryTest()
        {
            #region Chinese discovery test
            Dictionary<string, int> chMap = new Dictionary<string, int>();

            //List<KeyValuePair<string, string>> trainList = CharifyTargetWords(GetTabDelimitedPairs(@"C:\Users\jpaster2\Desktop\res\res\Chinese\chinese_full"),chMap);
            List<KeyValuePair<string, string>> trainList = UndotTargetWords(GetTabDelimitedPairs(@"C:\Users\jpaster2\Desktop\res\res\Chinese\chinese_full"));
            List<KeyValuePair<string, string>> wordList = GetRandomPartOfList(trainList, 700, 123);

            //StreamWriter writer = new StreamWriter(@"C:\Users\jpaster2\Desktop\res\res\Chinese\chinese_test_pairs.txt");
            //foreach (KeyValuePair<string,string> pair in wordList)
            //    writer.WriteLine(pair.Key + "\t" + pair.Value);

            //writer.Close();

            List<string> candidates = GetListValues(wordList);
            wordList.RemoveRange(600, 100);

            //DiscoveryTestDual(RemoveDuplicates(candidates), trainList, LiftPairList(wordList), 15, 15);
            DiscoveryEM(200, RemoveDuplicates(candidates), trainList, LiftPairList(wordList), new CSPModel(40, 0, 0, 0.000000000000001, SegMode.Entropy, false, SmoothMode.BySource, FallbackStrategy.Standard, EMMode.Normal,false));
            //TestXMLData(trainList, wordList, 15, 15);

            #endregion

        }

        public static void RussianDiscoveryTest()
        {
            #region Russian discovery test

            List<string> candidateList = new List<string>(File.ReadAllLines(@"C:\Users\jpaster2\Desktop\res\res\Russian\RussianWords"));
            for (int i = 0; i < candidateList.Count; i++) candidateList[i] = candidateList[i].ToLower();
            //candidateList.Clear();

            //Dictionary<string, List<string>> evalList = GetAlexData(@"C:\Users\jpaster2\Desktop\res\res\Russian\evalpairs.txt");//@"C:\Users\jpaster2\Desktop\res\res\Russian\evalpairs.txt");
            Dictionary<string, List<string>> evalList = GetAlexData(@"C:\Users\jpaster2\Desktop\res\res\Russian\evalpairsShort.txt");//@"C:\Users\jpaster2\Desktop\res\res\Russian\evalpairs.txt");

            //List<KeyValuePair<string, string>> trainList = NormalizeHebrew(GetTabDelimitedPairs(@"C:\Users\jpaster2\Desktop\res\res\Hebrew\train_EnglishHebrew.txt"));
            List<KeyValuePair<string, string>> trainList = GetTabDelimitedPairs(@"C:\Data\WikiTransliteration\Aliases\ruExamples.txt");
            //List<KeyValuePair<string, string>> trainList2 = RemoveVeryLong(NormalizeHebrew(GetTabDelimitedPairs(@"C:\Data\WikiTransliteration\Aliases\heExamples-Lax.txt")), 20);
            List<KeyValuePair<string, string>> trainList2 = new List<KeyValuePair<string, string>>(trainList.Count);

            Dictionary<string, bool> usedExamples = new Dictionary<string, bool>();
            foreach (KeyValuePair<string, List<string>> pair in evalList) usedExamples[pair.Key] = true;
            //trainList2 = TruncateList(trainList2, 2000);
            foreach (KeyValuePair<string, string> pair in trainList) if (!usedExamples.ContainsKey(pair.Key)) trainList2.Add(pair);

            DiscoveryEM(200, RemoveDuplicates(GetWords(evalList)), trainList2, evalList, new CSPModel(40, 0, 0, 0.000000000000001, SegMode.Entropy, false, SmoothMode.BySource, FallbackStrategy.Standard,EMMode.Normal,false));                                                                                         
            //DiscoveryTestDual(RemoveDuplicates(candidateList), trainList2, evalList, 15, 15);
            //DiscoveryTestDual(RemoveDuplicates(GetWords(evalList)), trainList2, evalList, 15, 15);

            #endregion
        }

        public static List<string> GetWords(Dictionary<string, List<string>> dict)
        {
            List<string> result = new List<string>();
            foreach (List<string> list in dict.Values) result.AddRange(list);
            return result;
        }

        public static Dictionary<string, List<string>> LiftPairList(List<KeyValuePair<string, string>> list)
        {
            Dictionary<string, List<string>> result = new Dictionary<string, List<string>>(list.Count);
            foreach (KeyValuePair<string, string> pair in list) result[pair.Key] = new List<string>(new string[] { pair.Value });

            return result;
        }

        public static List<KeyValuePair<string, string>> TruncateList(List<KeyValuePair<string, string>> list, int maxCount)
        {
            List<KeyValuePair<string, string>> result = new List<KeyValuePair<string,string>>(Math.Min(maxCount, list.Count));
            for (int i = 0; i < maxCount && i < list.Count; i++)
                result.Add(list[i]);

            return result;
        }

        private static List<KeyValuePair<string, string>> RemoveVeryLong(List<KeyValuePair<string, string>> list, int maxLength)
        {
            List<KeyValuePair<string, string>> result = new List<KeyValuePair<string, string>>(list.Count);
            foreach (KeyValuePair<string,string> pair in list)
            {
                if (pair.Key.Length <= maxLength && pair.Value.Length <= maxLength) result.Add(pair);
            }

            return result;
        }


        private static string Charify(string word, Dictionary<string,int> map)
        {
            string[] parts = word.Split('.');
            StringBuilder result = new StringBuilder(parts.Length);
            foreach (string part in parts)
            {
                if (!map.ContainsKey(part))
                    map[part] = map.Count;

                result.Append((char)map[part]);
            }

            return result.ToString();
        }
        private static List<KeyValuePair<string, string>> CharifyTargetWords(List<KeyValuePair<string, string>> list, Dictionary<string,int> map)
        {
            List<KeyValuePair<string, string>> result = new List<KeyValuePair<string, string>>(list.Count);
            foreach (KeyValuePair<string, string> pair in list)
            {
                result.Add(new KeyValuePair<string, string>(pair.Key, Charify(pair.Value, map)));
            }

            return result;
        }

        private static List<KeyValuePair<string, string>> UndotTargetWords(List<KeyValuePair<string, string>> list)
        {
            List<KeyValuePair<string, string>> result = new List<KeyValuePair<string, string>>(list.Count);
            foreach (KeyValuePair<string, string> pair in list)
            {
                result.Add(new KeyValuePair<string, string>(pair.Key, pair.Value.Replace(".","")));
            }

            return result;
        }

        private static List<string> RemoveDuplicates(List<string> list)
        {
            List<string> result = new List<string>(list.Count);
            Dictionary<string, bool> seen = new Dictionary<string, bool>();
            foreach (string s in list)
            {
                if (seen.ContainsKey(s)) continue;
                seen[s] = true;
                result.Add(s);
            }

            return result;
        }

        internal static List<Triple<string, string, double>> ConvertExamples(List<KeyValuePair<string, string>> examples)
        {
            List<Triple<string, string, double>> fExamples = new List<Triple<string, string, double>>(examples.Count);
            foreach (KeyValuePair<string, string> pair in examples)
                fExamples.Add(new Triple<string, string, double>(pair.Key, pair.Value, 1));

            return fExamples;
        }

        private static Dictionary<Pair<string, string>, double> MakeAlignmentTable(int maxSubstringLength1, int maxSubstringLength2, List<KeyValuePair<string, string>> examples, Dictionary<Pair<string, string>, double> probs, bool weightedAlignments)
        {
            List<Triple<string, string, double>> fExamples = new List<Triple<string, string, double>>(examples.Count);
            foreach (KeyValuePair<string, string> pair in examples)
                fExamples.Add(new Triple<string, string, double>(pair.Key, pair.Value, 1));

            return MakeAlignmentTable(maxSubstringLength1, maxSubstringLength2, fExamples, probs,weightedAlignments);
        }

        private static Dictionary<Pair<string, string>, double> MakeAlignmentTableLog(int maxSubstringLength1, int maxSubstringLength2, List<KeyValuePair<string, string>> examples, Dictionary<Pair<string, string>, double> probs, bool weightedAlignments)
        {
            List<Triple<string, string, double>> fExamples = new List<Triple<string, string, double>>(examples.Count);
            foreach (KeyValuePair<string, string> pair in examples)
                fExamples.Add(new Triple<string, string, double>(pair.Key, pair.Value, 1));

            return MakeAlignmentTableLog(maxSubstringLength1, maxSubstringLength2, fExamples, probs, weightedAlignments);
        }

        private static Dictionary<Pair<string, string>, double> MakeAlignmentTableLog(int maxSubstringLength1, int maxSubstringLength2, List<Triple<string, string, double>> examples, Dictionary<Pair<string, string>, double> probs, bool weightedAlignments)
        {
            Pasternack.Collections.Generic.Specialized.InternDictionary<string> internTable = new Pasternack.Collections.Generic.Specialized.InternDictionary<string>();
            Dictionary<Pair<string, string>, double> counts = new Dictionary<Pair<string, string>, double>();

            int alignmentCount = 0;
            foreach (Triple<string, string, double> example in examples)
            {
                string sourceWord = example.x;
                string bestWord = example.y;
                if (sourceWord.Length * maxSubstringLength2 >= bestWord.Length && bestWord.Length * maxSubstringLength1 >= sourceWord.Length)
                {
                    alignmentCount++;
                    Dictionary<Pair<string, string>, double> wordCounts;
                    if (weightedAlignments && probs != null)
                        wordCounts = WikiTransliteration.FindLogWeightedAlignments(sourceWord, bestWord, maxSubstringLength1, maxSubstringLength2, probs, internTable,true);
                    else
                        wordCounts = WikiTransliteration.FindLogAlignments(sourceWord, bestWord, maxSubstringLength1, maxSubstringLength2, internTable, true);

                    //if (!weightedAlignments && probs != null) wordCounts = SumNormalize(Dictionaries.Multiply<Pair<string, string>>(wordCounts, probs));

                    Dictionary<Pair<string, string>, double> expWordCounts = new Dictionary<Pair<string, string>, double>(wordCounts.Count);
                    foreach (KeyValuePair<Pair<string, string>, double> pair in wordCounts)
                        expWordCounts.Add(pair.Key, Math.Exp(pair.Value));

                    Dictionaries.AddTo<Pair<string, string>>(counts, expWordCounts, example.z);
                }
            }

            //Dictionary<Pair<string, string>, double> newCounts = new Dictionary<Pair<string, string>, double>(counts.Count);
            //foreach (KeyValuePair<Pair<string, string>, double> pair in counts)            
            //    newCounts.Add(pair.Key, pair.Value > 0 ? pair.Value : double.Epsilon);
            //counts = newCounts;

            Dictionary<string, double> totals1 = WikiTransliteration.GetAlignmentTotals1(counts);
            Dictionary<string, double> totals2 = WikiTransliteration.GetAlignmentTotals2(counts);
            Dictionary<Pair<string, string>, double> result = new Dictionary<Pair<string, string>, double>(counts.Count);
            foreach (KeyValuePair<Pair<string, string>, double> pair in counts)            
            {
                double value = (pair.Value == 0 ? 0 : (pair.Value / totals1[pair.Key.x]) * (pair.Value / totals2[pair.Key.y]));
                //if (double.IsNaN(value) || double.IsInfinity(value))
                //    return null; // Console.WriteLine("Bad!");
                result[pair.Key] = Math.Log(value);                
            }

            Console.WriteLine(alignmentCount + " words aligned.");

            return result;
        }        

        static Dictionary<Pair<string, string>, Dictionary<Pair<string, string>, double>> maxCache = new Dictionary<Pair<string, string>, Dictionary<Pair<string, string>, double>>();

        internal static Dictionary<Pair<string, string>, double> MakeRawAlignmentTable(int maxSubstringLength1, int maxSubstringLength2, List<Triple<string, string, double>> examples, Dictionary<Pair<string, string>, double> probs, WeightingMode weightingMode , NormalizationMode normalization, bool getExampleCounts, out List<List<KeyValuePair<Pair<string,string>,double>>> exampleCounts)
        {
            Pasternack.Collections.Generic.Specialized.InternDictionary<string> internTable = new Pasternack.Collections.Generic.Specialized.InternDictionary<string>();
            Dictionary<Pair<string, string>, double> counts = new Dictionary<Pair<string, string>, double>();
            exampleCounts = (getExampleCounts ? new List<List<KeyValuePair<Pair<string, string>, double>>>(examples.Count) : null);

            int alignmentCount = 0;
            foreach (Triple<string, string, double> example in examples)
            {
                string sourceWord = example.x;
                string bestWord = example.y;
                if (sourceWord.Length * maxSubstringLength2 >= bestWord.Length && bestWord.Length * maxSubstringLength1 >= sourceWord.Length)
                {
                    alignmentCount++;
                    Dictionary<Pair<string, string>, double> wordCounts;
                    if (weightingMode == WeightingMode.FindWeighted && probs != null)
                        wordCounts = WikiTransliteration.FindWeightedAlignments(sourceWord, bestWord, maxSubstringLength1, maxSubstringLength2, probs, internTable, normalization);
                    //wordCounts = WikiTransliteration.FindWeightedAlignmentsAverage(sourceWord, bestWord, maxSubstringLength1, maxSubstringLength2, probs, internTable, true, normalization);                                            
                    else if (weightingMode == WeightingMode.CountWeighted)
                        wordCounts = WikiTransliteration.CountWeightedAlignments(sourceWord, bestWord, maxSubstringLength1, maxSubstringLength2, probs, internTable, normalization, false);
                    else if (weightingMode == WeightingMode.MaxAlignment)
                    {
                        Dictionary<Pair<string, string>, double> cached = null;
                        if (!maxCache.TryGetValue(new Pair<string, string>(sourceWord, bestWord), out cached))
                            cached = new Dictionary<Pair<string, string>, double>();

                        Dictionaries.AddTo<Pair<string, string>>(probs, cached, -1);

                        wordCounts = WikiTransliteration.CountMaxAlignments(sourceWord, bestWord, maxSubstringLength1, probs, internTable, false);
                        maxCache[new Pair<string, string>(sourceWord, bestWord)] = wordCounts;

                        Dictionaries.AddTo<Pair<string, string>>(probs, cached, 1);
                    }
                    else if (weightingMode == WeightingMode.MaxAlignmentWeighted)
                        wordCounts = WikiTransliteration.CountMaxAlignments(sourceWord, bestWord, maxSubstringLength1, probs, internTable, true);
                    else //if (weightingMode == WeightingMode.None || weightingMode == WeightingMode.SuperficiallyWeighted)
                        wordCounts = WikiTransliteration.FindAlignments(sourceWord, bestWord, maxSubstringLength1, maxSubstringLength2, internTable, normalization);

                    if (weightingMode == WeightingMode.SuperficiallyWeighted && probs != null)
                        wordCounts = SumNormalize(Dictionaries.Multiply<Pair<string, string>>(wordCounts, probs));

                    Dictionaries.AddTo<Pair<string, string>>(counts, wordCounts, example.z);

                    if (getExampleCounts)
                    {
                        List<KeyValuePair<Pair<string, string>, double>> curExampleCounts = new List<KeyValuePair<Pair<string, string>, double>>(wordCounts.Count);
                        foreach (KeyValuePair<Pair<string, string>, double> pair in wordCounts)
                            curExampleCounts.Add(pair);

                        exampleCounts.Add(curExampleCounts);
                    }
                }
                else
                    if (getExampleCounts) exampleCounts.Add(null);
            }

            return counts;
        }

        public static ContextModel LearnContextModel(List<Triple<string, string, double>> examples, ContextModel model)
        {
            int alignmentCount = 0;
            ContextModel result = new ContextModel();
            result.segContextSize = model.segContextSize;
            result.productionContextSize = model.productionContextSize;
            result.maxSubstringLength = model.maxSubstringLength;

            WikiTransliteration.ExampleCounts totals = new WikiTransliteration.ExampleCounts();
            totals.counts = new SparseDoubleVector<Pair<Triple<string,string,string>,string>>();
            totals.notSegCounts = new SparseDoubleVector<Pair<string,string>>();
            totals.segCounts = new SparseDoubleVector<Pair<string,string>>();

            foreach (Triple<string, string, double> example in examples)
            {
                string sourceWord = example.x;
                string bestWord = example.y;
                if (sourceWord.Length * model.maxSubstringLength >= bestWord.Length && bestWord.Length * model.maxSubstringLength >= sourceWord.Length)
                {
                    WikiTransliteration.ExampleCounts exampleCounts = WikiTransliteration.CountWeightedAlignments2(model.productionContextSize, model.segContextSize, sourceWord, bestWord, model.maxSubstringLength, model.maxSubstringLength, model.productionProbs, model.segProbs);
                    totals.counts += exampleCounts.counts;
                    totals.segCounts += exampleCounts.segCounts;
                    totals.notSegCounts += exampleCounts.notSegCounts;
                }
            }

            InternDictionary<string> internTable = new InternDictionary<string>();
            result.productionProbs = CreateFallback(totals.counts, internTable);
            result.segProbs = CreateFallback(totals.segCounts, totals.notSegCounts, internTable);

            return result;
        }

        //public static SparseDoubleVector<Pair<Triple<string, string, string>, string>> PSecondGivenFirst(SparseDoubleVector<Pair<Triple<string, string, string>, string>> productionProbs)
        //{
        //    SparseDoubleVector<Pair<Triple<string, string, string>, string>> result = new SparseDoubleVector<Pair<Triple<string, string, string>, string>>();
        //    SparseDoubleVector<Triple<string, string, string>> totals = new SparseDoubleVector<Triple<string, string, string>>();
        //    foreach (KeyValuePair<Pair<Triple<string, string, string>, string>, double> pair in productionProbs)            
        //        totals[pair.Key.x] += pair.Value;

        //    foreach (KeyValuePair<Pair<Triple<string, string, string>, string>, double> pair in productionProbs)
        //        result[pair.Key] = pair.Value / totals[pair.Key.x];

        //    return result;
            
        //}

        private static Dictionary<Triple<string, string,string>, double> MakeRawAlignmentTableWithContext(int maxSubstringLength1, int maxSubstringLength2, List<Triple<string, string, double>> examples, Dictionary<Triple<string, string, string>, double> probs, int contextSize, bool fallback, bool weightByContextOnly, NormalizationMode normalization, bool getExampleCounts, out List<List<KeyValuePair<Triple<string, string, string>, double>>> exampleCounts)
        {
            Pasternack.Collections.Generic.Specialized.InternDictionary<string> internTable = new Pasternack.Collections.Generic.Specialized.InternDictionary<string>();
            Dictionary<Triple<string, string, string>, double> counts = new Dictionary<Triple<string, string, string>, double>();
            exampleCounts = (getExampleCounts ? new List<List<KeyValuePair<Triple<string,string, string>, double>>>(examples.Count) : null);

            int alignmentCount = 0;
            foreach (Triple<string, string, double> example in examples)
            {
                string sourceWord = example.x;
                string bestWord = example.y;
                if (sourceWord.Length * maxSubstringLength2 >= bestWord.Length && bestWord.Length * maxSubstringLength1 >= sourceWord.Length)
                {
                    alignmentCount++;
                    Dictionary<Triple<string, string, string>, double> wordCounts;
                    wordCounts = WikiTransliteration.CountWeightedAlignmentsWithContext(contextSize, sourceWord, bestWord, maxSubstringLength1, maxSubstringLength2, probs, internTable, normalization, weightByContextOnly, fallback);

                    Dictionaries.AddTo<Triple<string,string, string>>(counts, wordCounts, example.z);

                    if (getExampleCounts)
                    {
                        List<KeyValuePair<Triple<string, string, string>, double>> curExampleCounts = new List<KeyValuePair<Triple<string, string, string>, double>>(wordCounts.Count);
                        foreach (KeyValuePair<Triple<string, string, string>, double> pair in wordCounts)
                            curExampleCounts.Add(pair);

                        exampleCounts.Add(curExampleCounts);
                    }
                }
                else
                    if (getExampleCounts) exampleCounts.Add(null);
            }

            return counts;
        }


        /// <summary>
        /// Calculates a probability table for P(String2 | String1) * P(String1 | Length(String1)) == P(String2, String1 | Length(String1))
        /// </summary>
        /// 
        public static Dictionary<Pair<string, string>, double> PJointGivenLength(Dictionary<Pair<string, string>, double> counts, int maxSubstringLength)
        {
            double[] sums = GetSourceCountSumsByLength(counts, maxSubstringLength);

            //Dictionary<string, double> totals1 = WikiTransliteration.GetAlignmentTotals1(counts);
            Dictionary<Pair<string, string>, double> result = new Dictionary<Pair<string, string>, double>(counts.Count);
            foreach (KeyValuePair<Pair<string, string>, double> pair in counts)
            {
                double value = pair.Value == 0 ? 0 : (pair.Value / sums[pair.Key.x.Length-1]);
                result[pair.Key] = value;
            }

            return result;
        }

        public static Dictionary<string,double> PFirstGivenLength(Dictionary<Pair<string, string>, double> counts, int maxSubstringLength)
        {
            double[] sums = GetSourceCountSumsByLength(counts, maxSubstringLength);

            Dictionary<string, double> result = new Dictionary<string, double>();
            foreach (KeyValuePair<Pair<string, string>, double> pair in counts)
            {
                double value = pair.Value / sums[pair.Key.x.Length - 1];
                Dictionaries.IncrementOrSet<string>(result, pair.Key.x, value, value);
            }

            return result;
        }

        /// <summary>
        /// Gets an array of totals of counts of the source substring by length.
        /// </summary>
        public static double[] GetSourceCountSumsByLength(Dictionary<Pair<string, string>, double> counts, int maxSubstringLength)
        {
            double[] result = new double[maxSubstringLength];
            
            foreach (KeyValuePair<Pair<string, string>, double> pair in counts)            
                result[pair.Key.x.Length - 1] += pair.Value;            

            return result;
        }

        public static SparseDoubleVector<Pair<Triple<string, string, string>, string>> CreateFallback(SparseDoubleVector<Pair<Triple<string, string, string>, string>> counts, InternDictionary<string> internTable)
        {
            SparseDoubleVector<Pair<Triple<string, string, string>, string>> result = new SparseDoubleVector<Pair<Triple<string, string, string>, string>>();
            foreach (KeyValuePair<Pair<Triple<string, string, string>, string>, double> pair in counts)
            {
                for (int i = 0; i <= pair.Key.x.x.Length; i++)
                {
                    result[new Pair<Triple<string,string,string>,string>(
                        new Triple<string,string,string>(
                            internTable.Intern(pair.Key.x.x.Substring(i)),
                            internTable.Intern(pair.Key.x.y),
                            internTable.Intern(pair.Key.x.z.Substring(0,pair.Key.x.z.Length-i))),pair.Key.y)] += pair.Value;
                }
            }

            return result;
        }

        //Create fallback for segmentation model
        public static SparseDoubleVector<Pair<string, string>> CreateFallback(SparseDoubleVector<Pair<string, string>> segCounts, SparseDoubleVector<Pair<string, string>> notSegCounts, InternDictionary<string> internTable)
        {
            SparseDoubleVector<Pair<string, string>> segTotals = new SparseDoubleVector<Pair<string,string>>(segCounts.Count);
            SparseDoubleVector<Pair<string, string>> notSegTotals = new SparseDoubleVector<Pair<string, string>>(notSegCounts.Count);
            
            foreach (KeyValuePair<Pair<string,string>, double> pair in segCounts)
            {
                for (int i = 0; i <= pair.Key.x.Length; i++)
                {
                    string left = internTable.Intern( pair.Key.x.Substring(i) );
                    string right = internTable.Intern( pair.Key.y.Substring(0,pair.Key.y.Length-i) );
                    Pair<string,string> contextPair = new Pair<string,string>(left,right);
                    segTotals[contextPair] += pair.Value;
                    notSegTotals[contextPair] += notSegCounts[pair.Key];
                }
            }

            return segTotals / (segTotals + notSegTotals);
        }

        //public static SparseDoubleVector<Pair<string,string>> CreateFallback(SparseDoubleVector<Pair<Triple<string, string, string>, string>> counts, InternDictionary<string> internTable)
        //{
        //    SparseDoubleVector<Pair<Triple<string, string, string>, string>> result = new SparseDoubleVector<Pair<Triple<string, string, string>, string>>();
        //    foreach (KeyValuePair<Pair<Triple<string, string, string>, string>, double> pair in counts)
        //    {
        //        for (int i = 0; i <= pair.Key.x.x.Length; i++)
        //        {
        //            result[new Pair<Triple<string, string, string>, string>(
        //                new Triple<string, string, string>(
        //                    internTable.Intern(pair.Key.x.x.Substring(i)),
        //                    internTable.Intern(pair.Key.x.y),
        //                    internTable.Intern(pair.Key.x.z.Substring(0, pair.Key.x.z.Length - i))), pair.Key.y)] += pair.Value;
        //        }
        //    }
        //}

        /// <summary>
        /// Calculates a probability table for P(String2 | String1)
        /// </summary>
        public static SparseDoubleVector<Pair<Triple<string, string, string>, string>> PSecondGivenFirst(SparseDoubleVector<Pair<Triple<string, string, string>, string>> counts)
        {
            SparseDoubleVector<Triple<string,string,string>> totals = new SparseDoubleVector<Triple<string,string,string>>();
            foreach (KeyValuePair<Pair<Triple<string, string, string>, string>, double> pair in counts)
                totals[pair.Key.x] += pair.Value;

            SparseDoubleVector<Pair<Triple<string, string, string>, string>> result = new SparseDoubleVector<Pair<Triple<string, string, string>, string>>(counts.Count);
            foreach (KeyValuePair<Pair<Triple<string, string, string>, string>, double> pair in counts)
            {
                double total = totals[pair.Key.x];
                result[pair.Key] = total == 0 ? 0 : pair.Value / total;
            }

            return result;
        }

        /// <summary>
        /// Calculates a probability table for P(String2 | String1)
        /// </summary>
        public static SparseDoubleVector<Pair<string, string>> PSecondGivenFirst(Dictionary<Pair<string, string>, double> counts)        
        {
            Dictionary<string, double> totals1 = WikiTransliteration.GetAlignmentTotals1(counts);

            Dictionary<string, int> sourceCounts = new Dictionary<string, int>();
            foreach (KeyValuePair<Pair<string, string>, double> pair in counts)
                Dictionaries.IncrementOrSet<string>(sourceCounts, pair.Key.x, 1, 1);

            Dictionary<Pair<string, string>, double> result = new Dictionary<Pair<string, string>, double>(counts.Count);
            foreach (KeyValuePair<Pair<string, string>, double> pair in counts)
            {
                //double value = totals1[pair.Key.x] == 0 ? ((double)1)/sourceCounts[pair.Key.x] : (pair.Value / totals1[pair.Key.x]);
                double value = totals1[pair.Key.x] == 0 ? 0 : (pair.Value / totals1[pair.Key.x]);                
                result[pair.Key] = value;
            }

            return result;
        }

        public static Dictionary<Triple<string, string, string>, double> PSecondGivenFirst(Dictionary<Triple<string, string, string>, double> counts)
        {
            Dictionary<Pair<string,string>, double> totals1 = WikiTransliteration.GetAlignmentTotals1(counts);
            Dictionary<Triple<string, string, string>, double> result = new Dictionary<Triple<string, string, string>, double>(counts.Count);
            foreach (KeyValuePair<Triple<string, string, string>, double> pair in counts)
            {
                double value = pair.Value == 0 ? 0 : (pair.Value / totals1[pair.Key.XY]);
                result[pair.Key] = value;
            }

            return result;
        }

        public static Dictionary<Triple<string, string, string>, double> PFirstGivenSecond(Dictionary<Triple<string, string, string>, double> counts)
        {
            Dictionary<string, double> totals2 = WikiTransliteration.GetAlignmentTotals2(counts);
            Dictionary<Triple<string, string, string>, double> result = new Dictionary<Triple<string, string, string>, double>(counts.Count);
            foreach (KeyValuePair<Triple<string, string, string>, double> pair in counts)
            {
                double value = pair.Value == 0 ? 0 : (pair.Value / totals2[pair.Key.z]);
                result[pair.Key] = value;
            }

            return result;
        }

        /// <summary>
        /// Calculates a probability table for P(String2 | String1)
        /// </summary>
        public static Dictionary<Pair<string, string>, double> PSecondGivenFirst(string word1, string word2, int maxSubstringLength, List<KeyValuePair<Pair<string, string>, double>> exampleProductions, Map<string,string> countsMap, Dictionary<Pair<string, string>, double> counts)
        {            
            Dictionary<string, double> totals = new Dictionary<string, double>();
            foreach (KeyValuePair<Pair<string, string>, double> exampleProduction in exampleProductions)
            {
                if (totals.ContainsKey(exampleProduction.Key.x)) continue;
                double total = 0;

                //loop over all the productions
                foreach (KeyValuePair<string, string> pair in countsMap.GetPairsForKey(exampleProduction.Key.x))
                    total += counts[pair];

                totals[exampleProduction.Key.x] = total;
            }            
            
            Dictionary<Pair<string, string>, double> result = new Dictionary<Pair<string, string>, double>(exampleProductions.Count);
            foreach (KeyValuePair<Pair<string, string>, double> exampleProduction in exampleProductions)
            {
                double total = totals[exampleProduction.Key.x];
                if (exampleProduction.Value == 0 || total == 0) continue; //not a valid production                
                result[exampleProduction.Key] = (exampleProduction.Value / total);
            }

            return result;
        }

        /// <summary>
        /// Calculates a probability table for P(String2 | String1) * P(String1 | String2)
        /// </summary>
        public static SparseDoubleVector<Pair<string, string>> PMutualProduction(Dictionary<Pair<string, string>, double> counts)
        {
            Dictionary<string, double> totals1 = WikiTransliteration.GetAlignmentTotals1(counts);
            Dictionary<string, double> totals2 = WikiTransliteration.GetAlignmentTotals2(counts);
            Dictionary<Pair<string, string>, double> result = new Dictionary<Pair<string, string>, double>(counts.Count);
            foreach (KeyValuePair<Pair<string, string>, double> pair in counts)
            {
                double value = (pair.Value / totals1[pair.Key.x]) * (pair.Value / totals2[pair.Key.y]);
                if (double.IsNaN(value) || double.IsInfinity(value))
                    value = 0; //assume it's impossible return null; // Console.WriteLine("Bad!");
                result[pair.Key] = value;
            }

            return result;
        }

        /// <summary>
        /// Calculates P(String1 of size n, String2)
        /// </summary>
        /// <param name="counts"></param>
        /// <returns></returns>
        public static Dictionary<Pair<string, string>, double> PSemiJoint(Dictionary<Pair<string, string>, double> counts, int maxSubstringSize1)
        {
            double[] totals = new double[maxSubstringSize1];

            foreach (KeyValuePair<Pair<string, string>, double> pair in counts)
                totals[pair.Key.x.Length - 1] += pair.Value;

            Dictionary<Pair<string, string>, double> result = new Dictionary<Pair<string, string>, double>(counts.Count);
            foreach (KeyValuePair<Pair<string, string>, double> pair in counts)
            {
                double value = pair.Value / totals[pair.Key.x.Length-1];
                result[pair.Key] = value;
            }

            return result;
        }

        /// <summary>
        /// Calculates a probability table for P(String1, String2)
        /// </summary>
        public static SparseDoubleVector<Pair<string, string>> PJoint(Dictionary<Pair<string, string>, double> counts)
        {
            double total = 0;
            foreach (double val in counts.Values)
                total += val;

            Dictionary<Pair<string, string>, double> result = new Dictionary<Pair<string, string>, double>(counts.Count);
            foreach (KeyValuePair<Pair<string, string>, double> pair in counts)
            {
                double value = pair.Value / total;                
                result[pair.Key] = value;
            }

            return result;
        }

        private static Dictionary<Pair<string, string>, double> MakeAlignmentTable(int maxSubstringLength1, int maxSubstringLength2, List<Triple<string,string,double>> examples, Dictionary<Pair<string, string>, double> probs, bool weightedAlignments)
        {
            Pasternack.Collections.Generic.Specialized.InternDictionary<string> internTable = new Pasternack.Collections.Generic.Specialized.InternDictionary<string>();
            Dictionary<Pair<string,string>,double> counts = new Dictionary<Pair<string,string>,double>();

            int alignmentCount=0;
            foreach (Triple<string,string,double> example in examples)
            {
                string sourceWord = example.x;
                string bestWord = example.y;
                if (sourceWord.Length * maxSubstringLength2 >= bestWord.Length && bestWord.Length * maxSubstringLength1 >= sourceWord.Length)
                {
                    alignmentCount++;                    
                    Dictionary<Pair<string,string>,double> wordCounts;
                    if (weightedAlignments && probs != null)
                        wordCounts = WikiTransliteration.FindWeightedAlignments(sourceWord, bestWord, maxSubstringLength1,maxSubstringLength2, probs, internTable,NormalizationMode.AllProductions);
                    else                        
                        wordCounts = WikiTransliteration.FindAlignments(sourceWord, bestWord, maxSubstringLength1,maxSubstringLength2, internTable,NormalizationMode.AllProductions);                    

                    if (!weightedAlignments && probs != null) wordCounts = SumNormalize(Dictionaries.Multiply<Pair<string,string>>(wordCounts,probs));                    

                    Dictionaries.AddTo<Pair<string, string>>(counts, wordCounts,example.z);
                }
            }

            //Dictionary<Pair<string, string>, double> newCounts = new Dictionary<Pair<string, string>, double>(counts.Count);
            //foreach (KeyValuePair<Pair<string, string>, double> pair in counts)            
            //    newCounts.Add(pair.Key, pair.Value > 0 ? pair.Value : double.Epsilon);
            //counts = newCounts;

            Dictionary<string, double> totals1 = WikiTransliteration.GetAlignmentTotals1(counts);
            Dictionary<string, double> totals2 = WikiTransliteration.GetAlignmentTotals2(counts);
            Dictionary<Pair<string, string>, double> result = new Dictionary<Pair<string, string>, double>(counts.Count);
            foreach (KeyValuePair<Pair<string, string>, double> pair in counts)
            //result[pair.Key] = pair.Value * pair.Value / (totals1[pair.Key.x]*totals2[pair.Key.y]);
            {
                //if (pair.Value == 0)
                //    result[pair.Key] = double.Epsilon;
                //else
                {
                    double value = (pair.Value / totals1[pair.Key.x]) * (pair.Value / totals2[pair.Key.y]);
                    if (double.IsNaN(value) || double.IsInfinity(value))
                        return null; // Console.WriteLine("Bad!");
                    result[pair.Key] = value;
                }
            }

            Console.WriteLine(alignmentCount + " words aligned.");

            return result;
        }

        private static Dictionary<Pair<string, string>, double> SumNormalize(Dictionary<Pair<string, string>, double> vector)
        {
            Dictionary<Pair<string, string>, double> result = new Dictionary<Pair<string, string>, double>(vector.Count);
            double sum=0;
            foreach (double value in vector.Values)
                sum += value;

            foreach (KeyValuePair<Pair<string, string>, double> pair in vector)
                result[pair.Key] = pair.Value / sum;

            return result;
        }

        private static List<Triple<string, string,WordAlignment>> GetTrainingExamples(string translationMapFile)
        {
            List<Triple<string, string, WordAlignment>> result = new List<Triple<string, string, WordAlignment>>();

            Dictionary<Pasternack.Utility.Pair<string, string>, WordAlignment> weights;
            Map<string, string> translationMap = ReadTranslationMap2(translationMapFile, out weights);            

            foreach (string sourceWord in translationMap.Keys)
            {
                WordAlignment maxWeight = new WordAlignment(0,0,0); string bestWord = null;
                foreach (string targetWord in translationMap.GetValuesForKey(sourceWord))
                    if (weights[new Pair<string, string>(sourceWord, targetWord)] > maxWeight)
                    {
                        maxWeight = weights[new Pair<string, string>(sourceWord, targetWord)];
                        bestWord = targetWord;
                    }

                if (maxWeight.oneToOne >= 1 && !Regex.IsMatch(bestWord,"^[A-Za-z0-9]+$",RegexOptions.Compiled))
                {                    
                    result.Add(new Triple<string,string,WordAlignment>(sourceWord,bestWord,maxWeight));
                    //Dictionaries.Add<Pair<string, string>>(counts, oneAlignmentPerWord ? WikiTransliteration.FindAlignments(sourceWord, bestWord, maxSubstringLength, internTable) : WikiTransliteration.CountAlignments(sourceWord, bestWord, maxSubstringLength, internTable), 1);
                }
            }

            return result;
        }

        public static void CheckTopList(TopList<double, string> list)
        {
            Dictionary<string,bool> dict = new Dictionary<string,bool>(list.Count);
            foreach (string val in list.Values)
                if (dict.ContainsKey(val)) throw new InvalidOperationException();
                else dict[val] = true;
        }

        //public static void TestXMLData2(string trainingFile, string testFile, int maxSubstringLength1, int maxSubstringLength2)
        //{
        //    List<KeyValuePair<string, string>> trainingPairs = GetTaskPairs(trainingFile);
        //    List<KeyValuePair<string, string>> testingPairs = GetTaskPairs(testFile);

        //    List<string> languageExamples = new List<string>(trainingPairs.Count);
        //    foreach (KeyValuePair<string, string> pair in trainingPairs)
        //        languageExamples.Add(pair.Value);
        //    Dictionary<string, int> ngramCounts = null;  WikiTransliteration.GetNgramCounts(3, languageExamples);

        //    Dictionary<Pair<string, string>, double> probs = MakeAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingPairs, null,false);

        //    Map<string, string> probMap = WikiTransliteration.GetProbMap(probs);
        //    //Dictionary<string, Pair<string, double>> maxProbs = WikiTransliteration.GetMaxProbs(probs);

        //    int correct = 0;
        //    int contained = 0;
        //    double mrr = 0;
        //    foreach (KeyValuePair<string, string> pair in testingPairs)
        //    {
        //        TopList<double, string> predictions = WikiTransliteration.Predict(20, pair.Key, maxSubstringLength1, probMap,probs, new Dictionary<string, TopList<double, string>>(),ngramCounts,3);
        //        CheckTopList(predictions);
        //        int position = predictions.Values.IndexOf(pair.Value);
        //        if (position == 0)
        //            correct++;

        //        if (position >= 0)
        //            contained++;

        //        if (position < 0)
        //            position = 20;

        //        mrr += 1 / ((double)position + 1);
        //    }

        //    mrr /= testingPairs.Count;

        //    Console.WriteLine(testingPairs.Count + " pairs tested in total.");
        //    Console.WriteLine(contained + " predictions contained (" + (((double)contained) / testingPairs.Count) + ")");
        //    Console.WriteLine(correct + " predictions exactly correct (" + (((double)correct) / testingPairs.Count) + ")");
        //    Console.WriteLine("MRR: " + mrr);
        //    Console.ReadLine();
        //}

        public static void Reweight(Dictionary<Pair<string, string>, double> rawProbs, List<Triple<string, string, double>> examples, List<List<KeyValuePair<Pair<string, string>, double>>> exampleCounts, int maxSubstringLength)
        {
            Map<string,string> rawMap = new Map<string,string>();
            foreach (Pair<string, string> pair in rawProbs.Keys)
                rawMap.Add(pair);

            double maxWeight = 0;
            for (int i = 0; i < examples.Count; i++)
            {
                List<KeyValuePair<Pair<string, string>, double>> curExampleCounts = exampleCounts[i];
                if (curExampleCounts == null) //alignment impossible
                {
                    examples[i] = new Triple<string,string,double>(examples[i].x,examples[i].y,0);
                    continue;
                }

                double oldWeight = examples[i].z;

                foreach (KeyValuePair<Pair<string, string>, double> pair in curExampleCounts)
                {
                    rawProbs[pair.Key] = rawProbs[pair.Key] - (oldWeight * pair.Value);
                }

                //double newWeight = WikiTransliteration.GetAlignmentProbability(examples[i].x, examples[i].y, maxSubstringLength, PSecondGivenFirst(examples[i].x,examples[i].y, maxSubstringLength, curExampleCounts, rawMap,rawProbs), 0);
                Dictionary<Pair<string,string>,double> probs = PSecondGivenFirst(examples[i].x,examples[i].y, maxSubstringLength, curExampleCounts, rawMap,rawProbs);
                Map<string,string> probMap = new Map<string,string>();
                foreach (Pair<string, string> pair in probs.Keys)
                    probMap.Add(pair);

                double newWeight = 0.5;
                if (WikiTransliteration.Predict(1, examples[i].x, maxSubstringLength, probMap, probs, new Dictionary<string, TopList<double, string>>(), null, 4).Values.Contains(examples[i].y))
                    newWeight = 1;

                //int index = WikiTransliteration.Predict(20, examples[i].x, maxSubstringLength, probMap, probs, new Dictionary<string, TopList<double, string>>(), null, 4).Values.IndexOf(examples[i].y);
                //if (index >= 0)
                //    newWeight = 1d / (index + 1);

                foreach (KeyValuePair<Pair<string, string>, double> pair in curExampleCounts)
                {
                    rawProbs[pair.Key] = rawProbs[pair.Key] + (oldWeight * pair.Value);
                }

                maxWeight = Math.Max(maxWeight, newWeight);
                examples[i] = new Triple<string, string, double>(examples[i].x, examples[i].y, newWeight);
            }

            for (int i = 0; i < examples.Count; i++)            
                examples[i] = new Triple<string,string,double>(examples[i].x,examples[i].y,examples[i].z/maxWeight);
        }

        public static List<KeyValuePair<string, string>> FilterExamplePairs(List<KeyValuePair<string, string>> examplePairs)
        {            
            List<KeyValuePair<string, string>> result = new List<KeyValuePair<string, string>>(examplePairs.Count);
            foreach (KeyValuePair<string, string> examplePair in examplePairs)
                result.Add(new KeyValuePair<string, string>(Regex.Replace(examplePair.Key, @"[`'ʻ-]", "", RegexOptions.Compiled), examplePair.Value));

            return result;
        }

        public static List<string> FilterExampleWords(List<string> exampleWords)
        {
            List<string> result = new List<string>(exampleWords.Count);
            foreach (string exampleWord in exampleWords)
                result.Add(Regex.Replace(exampleWord, @"[`'ʻ-]", "", RegexOptions.Compiled));

            return result;
        }

        public static Dictionary<string, double> UniformLanguageModel(Dictionary<string, double> languageModel)
        {
            Dictionary<string, double> result = new Dictionary<string, double>(languageModel.Count);
            foreach (KeyValuePair<string, double> pair in languageModel)
                result[pair.Key] = 1;

            return result;
        }

        public static void TestXMLDataOldStyle(string trainingFile, string testFile, int maxSubstringLength1, int maxSubstringLength2)
        {
            //Console
            int ngramSize = 6;
            List<KeyValuePair<string, string>> trainingPairs = FilterExamplePairs(GetTaskPairs(trainingFile));


            List<Triple<string, string, double>> trainingTriples = ConvertExamples(trainingPairs);
            List<KeyValuePair<string, string>> testingPairs = FilterExamplePairs(GetTaskPairs(testFile));

            List<string> trainingWords = new List<string>(trainingPairs.Count);
            foreach (KeyValuePair<string, string> pair in trainingPairs)
                trainingWords.Add(pair.Value);
            Dictionary<string, double> languageModel = WikiTransliteration.GetNgramProbs(1, ngramSize, trainingWords);

            List<List<KeyValuePair<Pair<string, string>, double>>> exampleCounts;

            Dictionary<Pair<string, string>, double> probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, null, WeightingMode.None, NormalizationMode.BySourceSubstring, false, out exampleCounts);
            EvaluateExamples(testingPairs, PMutualProduction(probs), maxSubstringLength1, null, ngramSize, 20, false, 0);
            //EvaluateExamples(testingPairs, PMutualProduction(probs), maxSubstringLength1, languageModel, ngramSize, 200, true);

            for (int i = 0; i < 500; i++)
            {
                //probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, PMutualProduction(probs), true,NormalizationMode.AllProductions, true, out exampleCounts);
                probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, PSemiJoint(probs, maxSubstringLength1), WeightingMode.SuperficiallyWeighted, NormalizationMode.BySourceSubstring, false, out exampleCounts);
                if (probs == null) break;
                
                Console.WriteLine("Iteration #" + i);
                EvaluateExamples(testingPairs, PMutualProduction(probs), maxSubstringLength1, null, ngramSize, 20, false, 0);
                EvaluateExamples(testingPairs, PMutualProduction(probs), maxSubstringLength1, languageModel, ngramSize, 200, true, 0);
            }

            Console.WriteLine("Finished.");
            Console.ReadLine();
        }

        //This method gets a surprising 60% correct on the test data
        public static void TestXMLDataOldOldStyle(string trainingFile, string testFile, int maxSubstringLength1, int maxSubstringLength2)
        {
            //Console
            int ngramSize = 4;
            List<KeyValuePair<string, string>> trainingPairs = FilterExamplePairs(GetTaskPairs(trainingFile));


            List<Triple<string, string, double>> trainingTriples = ConvertExamples(trainingPairs);
            List<KeyValuePair<string, string>> testingPairs = FilterExamplePairs(GetTaskPairs(testFile));

            List<string> trainingWords = new List<string>(trainingPairs.Count);
            foreach (KeyValuePair<string, string> pair in trainingPairs)
                trainingWords.Add(pair.Value);
            Dictionary<string, double> languageModel = WikiTransliteration.GetNgramProbs(1, ngramSize, trainingWords);

            List<List<KeyValuePair<Pair<string, string>, double>>> exampleCounts;

            Dictionary<Pair<string, string>, double> probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, null, WeightingMode.None, NormalizationMode.AllProductions, false, out exampleCounts);
            EvaluateExamples(testingPairs, PMutualProduction(probs), maxSubstringLength1, null, ngramSize, 20, false, 0);
            //EvaluateExamples(testingPairs, PMutualProduction(probs), maxSubstringLength1, languageModel, ngramSize, 200, true);

            for (int i = 0; i < 500; i++)
            {
                //probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, PMutualProduction(probs), true,NormalizationMode.AllProductions, true, out exampleCounts);
                probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, PMutualProduction(probs), WeightingMode.FindWeighted, NormalizationMode.AllProductions, false, out exampleCounts);
                if (probs == null) break;

                Console.WriteLine("Iteration #" + i);
                EvaluateExamples(testingPairs, PMutualProduction(probs), maxSubstringLength1, null, ngramSize, 20, false, 0);
                EvaluateExamples(testingPairs, PMutualProduction(probs), maxSubstringLength1, languageModel, ngramSize, 10, true, 0);
            }

            Console.WriteLine("Finished.");
            Console.ReadLine();
        }        

        public static Dictionary<string, double> GetNgramCounts(List<KeyValuePair<string, string>> examples, int maxSubstringLength)
        {
            InternDictionary<string> internTable = new InternDictionary<string>();
            Dictionary<string, double> result = new Dictionary<string, double>();

            List<string> exampleSources = new List<string>(examples.Count);

            foreach (KeyValuePair<string, string> example in examples)            
                exampleSources.Add(example.Key);

            return WikiTransliteration.GetNgramCounts(1, maxSubstringLength, exampleSources, false);
        }

        public static Dictionary<string, double> GetNgramCounts(List<string> examples, int maxSubstringLength)
        {
            return WikiTransliteration.GetNgramCounts(1, maxSubstringLength, examples, false);
        }

        public static SparseDoubleVector<Pair<string, string>> MultiPair(Dictionary<string, double> vector1, Dictionary<Pair<string, string>, double> vector2)
        {
            Dictionary<Pair<string, string>, double> result = new Dictionary<Pair<string, string>, double>(vector2.Count);

            foreach (KeyValuePair<Pair<string, string>, double> pair in vector2)
            {                
                result[pair.Key] = vector1.ContainsKey(pair.Key.x) ? pair.Value * vector1[pair.Key.x] : 0;
            }

            return result;
        }

        public static SparseDoubleVector<Pair<string, string>> MultiPair2(Dictionary<string, double> vector1, Dictionary<Pair<string, string>, double> vector2)
        {
            Dictionary<Pair<string, string>, double> result = new Dictionary<Pair<string, string>, double>(vector2.Count);

            foreach (KeyValuePair<Pair<string, string>, double> pair in vector2)
            {
                result[pair.Key] = vector1.ContainsKey(pair.Key.y) ? pair.Value * vector1[pair.Key.y] : 0;
            }

            return result;
        }

        public static void TestXMLData(string trainingFile, string testFile, int maxSubstringLength1, int maxSubstringLength2)
        {
            List<KeyValuePair<string, string>> trainingPairs = FilterExamplePairs(GetTaskPairs(trainingFile));
            List<KeyValuePair<string, string>> testingPairs = FilterExamplePairs(GetTaskPairs(testFile));

            TestXMLData(trainingPairs, testingPairs, maxSubstringLength1, maxSubstringLength2);
        }

        public static string Reverse(string word)
        {
            char[] wordChars = new char[word.Length];
            for (int i = 0; i < word.Length; i++)
                wordChars[wordChars.Length - 1 - i] = word[i];

            return new string(wordChars);
        }

        public static List<KeyValuePair<string, string>> ReverseTargetWord(List<KeyValuePair<string, string>> wordPairs)
        {
            List<KeyValuePair<string, string>> result = new List<KeyValuePair<string, string>>(wordPairs.Count);
            foreach (KeyValuePair<string, string> pair in wordPairs)
            {
                result.Add(new KeyValuePair<string, string>(pair.Key, Reverse(pair.Value)));
            }

            return result;
        }

        public static List<KeyValuePair<string, string>> GetTabDelimitedPairs(string filename)
        {
            List<KeyValuePair<string, string>> result = new List<KeyValuePair<string, string>>();
            string[] lines = File.ReadAllLines(filename);
            foreach (string line in lines)
            {
                string[] pair = line.Trim().Split('\t');
                if (pair.Length != 2) continue;
                result.Add(new KeyValuePair<string, string>(pair[0].Trim().ToLower(), pair[1].Trim().ToLower()));
            }
            
            return result;
        }

        //public static List<KeyValuePair<string, string>> GetPartOfList(List<KeyValuePair<string, string>> wordList, int start, int count)
        //{
        //    if (count < 0) count = wordList.Count - start;

        //    List<KeyValuePair<string, string>> result = new List<KeyValuePair<string,string>>(count);

        //    for (int i = start; i < start + count; i++)
        //        result.Add(wordList[i]);

        //    return result;
        //}

        /// <summary>
        /// Returns a random subset of a list; the list provided is modified to remove the selected items.
        /// </summary>
        /// <param name="wordList"></param>
        /// <param name="count"></param>
        /// <param name="seed"></param>
        /// <returns></returns>
        public static List<KeyValuePair<string, string>> GetRandomPartOfList(List<KeyValuePair<string, string>> wordList, int count, int seed)
        {
            Random r = new Random(seed);

            List<KeyValuePair<string,string>> randomList = new List<KeyValuePair<string,string>>(count);

            for (int i = 0; i < count; i++)
            {
                int index = r.Next(wordList.Count);
                randomList.Add(wordList[index]);
                wordList.RemoveAt(index);
            }

            return randomList;
        }

        public static List<string> GetListValues(List<KeyValuePair<string, string>> wordList)
        {
            List<string> valueList = new List<string>(wordList.Count);
            foreach (KeyValuePair<string, string> pair in wordList)
                valueList.Add(pair.Value);

            return valueList;
        }

        public static List<KeyValuePair<string, string>> InvertPairs(List<KeyValuePair<string, string>> pairs)
        {
            List<KeyValuePair<string, string>> result = new List<KeyValuePair<string, string>>(pairs.Count);
            foreach (KeyValuePair<string, string> pair in pairs)
                result.Add(new KeyValuePair<string, string>(pair.Value, pair.Key));

            return result;
        }

        public static void ContextDiscoveryTest(List<string> candidateWords, List<KeyValuePair<string, string>> trainingPairs, Dictionary<string, List<string>> testingPairs, int maxSubstringLength, int productionContextSize, int segContextSize)
        {
            double minProductionProbability = 0.000000000000001;
            //double minProductionProbability = 0.00000001;

            List<Triple<string, string, double>> trainingTriples = ConvertExamples(trainingPairs);                       

            ContextModel model = LearnContextModel(trainingTriples, new ContextModel());
            //probs = MultiPair(languageModel,probs);

            for (int i = 0; i < 2000; i++)
            {
                Console.WriteLine("Iteration #" + i);
                

                //probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, PMutualProduction(probs), WeightingMode.CountWeighted, NormalizationMode.None, true, out exampleCounts);
                
                    //EvaluateExamples(testingPairs, candidateWords, PMutualProduction(probs), maxSubstringLength1, true, minProductionProbability);                                   
            }

            Console.WriteLine("Finished."); Console.ReadLine();
        }

        //long Choose(long n, long k)
        //{
        //    long result = 1;

        //    for (long i = Math.Max(k, n - k) + 1; i <= n; ++i)
        //        result *= i;

        //    for (long i = 2; i <= Math.Min(k, n - k); ++i)
        //        result /= i;

        //    return result;
        //}

        static double Choose(double n, double k)
        {
            double result = 1;

            for (double i = Math.Max(k, n - k) + 1; i <= n; ++i)
                result *= i;

            for (double i = 2; i <= Math.Min(k, n - k); ++i)
                result /= i;

            return result;
        }

        public static double[][] SegmentationCounts(int maxLength)
        {
            double[][] result = new double[maxLength][];
            for (int i = 0; i < maxLength; i++)
            {
                result[i] = new double[i+1];
                for (int j = 0; j <= i; j++)
                    result[i][j] = Choose(i, j);
            }

            return result;
        }

        public static double[][] SegSums(int maxLength)
        {
            double[][] segmentationCounts = SegmentationCounts(maxLength);
            double[][] result = new double[maxLength][];
            for (int i = 0; i < maxLength; i++)
            {
                result[i] = new double[maxLength];
                for (int j = 0; j < maxLength; j++)
                {
                    int minIJ = Math.Min(i,j);
                    for (int k = 0; k <= minIJ; k++)
                        result[i][j] += segmentationCounts[i][k] * segmentationCounts[j][k];// *Math.Pow(0.5, k + 1);
                }
            }

            return result;
        }

        public static double[][] segSums = SegSums(40);

        public static void DiscoveryTest(List<string> candidateWords, List<KeyValuePair<string, string>> trainingPairs, Dictionary<string, List<string>> testingPairs, int maxSubstringLength1, int maxSubstringLength2)
        {
            double minProductionProbability = 0.000000000000001;
            //double minProductionProbability = 0.00000001;
           
            List<Triple<string, string, double>> trainingTriples = ConvertExamples(trainingPairs);
                                    
            List<List<KeyValuePair<Pair<string, string>, double>>> exampleCounts;

            //SparseDoubleVector<string> testingModel = (SparseDoubleVector<string>)GetNgramCounts(testingPairs, maxSubstringLength1);
            //SparseDoubleVector<string> languageModel = testingModel + GetNgramCounts(trainingPairs,maxSubstringLength1);
            //languageModel *= testingModel.Sign(); //eliminate all elements not in the testing examples
            //languageModel /= testingModel.PNorm(1); //marginalize to get P(S)
            
            SparseDoubleVector<Pair<string, string>> probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, null, WeightingMode.None, NormalizationMode.None, false, out exampleCounts);
            //probs = MultiPair(languageModel,probs);

            EvaluateExamples(testingPairs, candidateWords, PSecondGivenFirst(probs), maxSubstringLength1, true, minProductionProbability);

            for (int i = 0; i < 2000; i++)
            {                
                //probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, (SparseDoubleVector<Pair<string, string>>)PSecondGivenFirst(probs)*.5, WeightingMode.CountWeighted, NormalizationMode.None, true, out exampleCounts);
                probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, PSecondGivenFirst(probs), WeightingMode.CountWeighted, NormalizationMode.None, true, out exampleCounts);
                //probs += (0.00001*probs.PNorm(1));
                //probs = MultiPair(languageModel.Sign(), probs);

                Console.WriteLine("Iteration #" + i);                
                {                    
                    //EvaluateExamples(testingPairs, candidateWords, (SparseDoubleVector<Pair<string, string>>)PSecondGivenFirst(probs)*.5, maxSubstringLength1, true, minProductionProbability);
                    //EvaluateExamples(testingPairs, candidateWords, (SparseDoubleVector<Pair<string, string>>)PSecondGivenFirst(probs) * .5, maxSubstringLength1, false, minProductionProbability);
                    EvaluateExamples(testingPairs, candidateWords, PSecondGivenFirst(probs), maxSubstringLength1, true, minProductionProbability);
                    //EvaluateExamples(testingPairs, candidateWords, PSecondGivenFirst(probs), maxSubstringLength1, false, minProductionProbability);          
                }

                //Reweight(probs, trainingTriples, exampleCounts, maxSubstringLength1);
            }

            Console.WriteLine("Finished."); Console.ReadLine();
        }

        public static void DiscoveryEM(int iterations, List<string> candidateWords, List<KeyValuePair<string, string>> trainingPairs, Dictionary<string, List<string>> testingPairs, TransliterationModel model)
        {            
            List<Triple<string, string, double>> trainingTriples = ConvertExamples(trainingPairs);

            //CSPModel sM = new CSPModel();
            //sM.segProbs = CSPTransliteration.PSegGivenLength(LiftSegProbs(GetNgramCounts(trainingPairs, ((CSPModel)model).maxSubstringLength)));
            //sM.segProbs = CSPTransliteration.PSegGivenLength(LiftSegProbs(GetNgramCounts(new List<string>(testingPairs.Keys), ((CSPModel)model).maxSubstringLength)));
            //List<List<KeyValuePair<Pair<string, string>, double>>> exampleCounts;
            //((CSPModel)model).productionProbs = LiftProbs(PSecondGivenFirst(MakeRawAlignmentTable(((CSPModel)model).maxSubstringLength, ((CSPModel)model).maxSubstringLength, trainingTriples, null, WeightingMode.None, NormalizationMode.None, false, out exampleCounts)));
            //DiscoveryEvaluation(testingPairs, candidateWords, model);
           
            //model = model.LearnModel(trainingTriples);

            //((CSPModel)model).segProbs = new SparseDoubleVector<Triple<string,string,string>>();
            //foreach (Pair<Triple<string, string, string>, string> pair in ((CSPModel)model).productionProbs.Keys)
            //    ((CSPModel)model).segProbs[pair.x] = Math.Pow(0.2, Math.Abs(2 - pair.x.y.Length));

            for (int i = 0; i < iterations; i++)
            {
                Console.WriteLine("Iteration #" + i);
                DateTime startTime = DateTime.Now;
                Console.Write("Training...");
                model = model.LearnModel(trainingTriples);
                Console.WriteLine("Finished in " + ((TimeSpan)(DateTime.Now - startTime)).TotalSeconds + " seconds.");
                //((CSPModel)model).segProbs = null;
                //if (i == 0) sM.segProbs = ((CSPModel)model).segProbs; else ((CSPModel)model).segProbs = sM.segProbs;
                
                //((CSPModel)model).segProbs = sM.segProbs;
                //((CSPModel)model).segProbs = ((CSPModel)model).segProbs * (sM.segProbs + ((CSPModel)model).segProbs.Filter( delegate(Triple<string,string,string> arg){return arg.y.Length==1;})).Sign() ;
                //if (i>7)
                DiscoveryEvaluation(testingPairs, candidateWords, model);
            }

            Console.WriteLine("Finished."); Console.ReadLine();
        }

        public static SparseDoubleVector<Pair<Triple<string, string, string>, string>> LiftProbs(SparseDoubleVector<Pair<string, string>> probs)
        {
            SparseDoubleVector<Pair<Triple<string, string, string>, string>> result = new SparseDoubleVector<Pair<Triple<string, string, string>, string>>(probs.Count);
            foreach (KeyValuePair<Pair<string, string>, double> pair in probs)
                result.Add(new Pair<Triple<string, string, string>, string>(new Triple<string, string, string>("", pair.Key.x, ""), pair.Key.y), pair.Value);

            return result;
        }

        public static SparseDoubleVector<Triple<string, string, string>> LiftSegProbs(SparseDoubleVector<string> probs)
        {
            SparseDoubleVector<Triple<string, string, string>> result = new SparseDoubleVector<Triple<string,string,string>>(probs.Count);
            foreach (KeyValuePair<string, double> pair in probs)
                result.Add(new Triple<string, string, string>("", pair.Key, ""), pair.Value);

            return result;
        }

        public static SparseDoubleVector<Pair<string, string>> InvertProbs(SparseDoubleVector<Pair<string, string>> probs)
        {
            SparseDoubleVector<Pair<string, string>> result = new SparseDoubleVector<Pair<string, string>>(probs.Count);
            foreach (KeyValuePair<Pair<string, string>, double> pair in probs)
                result.Add(new Pair<string, string>(pair.Key.y, pair.Key.x), pair.Value);
            return result;
        }

        public static void DiscoveryGenerationTest(List<string> candidateWords, List<KeyValuePair<string, string>> trainingPairs, Dictionary<string, List<string>> testingPairs, int maxSubstringLength1, int maxSubstringLength2)
        {
            double minProductionProbability = 0.000000000000001;
            //double minProductionProbability = 0.00000001;

            List<Triple<string, string, double>> trainingTriples = ConvertExamples(trainingPairs);

            SparseDoubleVector<string> possibleProductions = new SparseDoubleVector<string>();
            foreach (string s in candidateWords)
            {
                for (int i = 0; i < s.Length; i++)
                    for (int j = 1; i + j <= s.Length; j++)
                        possibleProductions[s.Substring(i, j)] = 1;
            }

            List<List<KeyValuePair<Pair<string, string>, double>>> exampleCounts;

            SparseDoubleVector<Pair<string, string>> probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, null, WeightingMode.None, NormalizationMode.None, false, out exampleCounts);

            for (int i = 0; i < 2000; i++)
            {                
                probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, PSecondGivenFirst(probs), WeightingMode.CountWeighted, NormalizationMode.None, true, out exampleCounts);                

                Console.WriteLine("Iteration #" + i);
                {
                    SparseDoubleVector<Pair<string, string>> probs2 = MultiPair2(possibleProductions, PSecondGivenFirst(probs));
                    probs2.RemoveRedundantElements();

                    //EvaluateExamples(testingPairs, candidateWords, (SparseDoubleVector<Pair<string, string>>)PSecondGivenFirst(probs)*.5, maxSubstringLength1, true, minProductionProbability);
                    //EvaluateExamples(testingPairs, candidateWords, (SparseDoubleVector<Pair<string, string>>)PSecondGivenFirst(probs) * .5, maxSubstringLength1, false, minProductionProbability);
                    EvaluateExamplesDiscoveryGeneration(testingPairs, candidateWords, probs2, maxSubstringLength1, true, 100);
                    //EvaluateExamplesDual(testingPairs, candidateWords, PSecondGivenFirst(probs), PSecondGivenFirst(probsDual), maxSubstringLength1, true, minProductionProbability);
                    //EvaluateExamplesDual(testingPairs, candidateWords, PSecondGivenFirst(probs), PSecondGivenFirst(probsDual), maxSubstringLength1, true, minProductionProbability, 0);
                    //EvaluateExamples(testingPairs, candidateWords, PSecondGivenFirst(probs)* InvertProbs( PSecondGivenFirst(probsDual) ), maxSubstringLength1, true, minProductionProbability*minProductionProbability);
                }

                //Reweight(probs, trainingTriples, exampleCounts, maxSubstringLength1);
            }

            Console.WriteLine("Finished."); Console.ReadLine();
        }

        public static void DiscoveryTestDual(List<string> candidateWords, List<KeyValuePair<string, string>> trainingPairs, Dictionary<string,List<string>> testingPairs, int maxSubstringLength1, int maxSubstringLength2)
        {
            double minProductionProbability = 0.000000000000001;
            //double minProductionProbability = 0.00000001;

            List<Triple<string, string, double>> trainingTriples = ConvertExamples(trainingPairs);
            List<Triple<string, string, double>> trainingTriplesDual = ConvertExamples( InvertPairs( trainingPairs ));

            List<List<KeyValuePair<Pair<string, string>, double>>> exampleCounts;            

            SparseDoubleVector<Pair<string, string>> probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, null, WeightingMode.None, NormalizationMode.None, false, out exampleCounts);
            SparseDoubleVector<Pair<string, string>> probsDual = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriplesDual, null, WeightingMode.None, NormalizationMode.None, false, out exampleCounts);
            //probs = MultiPair(languageModel,probs);

            for (int i = 0; i < 2000; i++)
            {
                //probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, (SparseDoubleVector<Pair<string, string>>)PSecondGivenFirst(probs)*.5, WeightingMode.CountWeighted, NormalizationMode.None, true, out exampleCounts);
                probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, PSecondGivenFirst(probs), WeightingMode.CountWeighted, NormalizationMode.None, true, out exampleCounts);
                probsDual = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriplesDual, PSecondGivenFirst(probsDual), WeightingMode.CountWeighted, NormalizationMode.None, true, out exampleCounts);
                //probs += (0.00001*probs.PNorm(1));
                //probs = MultiPair(languageModel.Sign(), probs);

                Console.WriteLine("Iteration #" + i);
                {
                    //if (i < 9) continue;

                    //EvaluateExamples(testingPairs, candidateWords, (SparseDoubleVector<Pair<string, string>>)PSecondGivenFirst(probs)*.5, maxSubstringLength1, true, minProductionProbability);
                    //EvaluateExamples(testingPairs, candidateWords, (SparseDoubleVector<Pair<string, string>>)PSecondGivenFirst(probs) * .5, maxSubstringLength1, false, minProductionProbability);
                    
                    //EvaluateExamplesDual(testingPairs, candidateWords, PSecondGivenFirst(probs), PSecondGivenFirst(probsDual), maxSubstringLength1, true, minProductionProbability);
                    EvaluateExamplesDual(testingPairs, candidateWords, PSecondGivenFirst(probs), PSecondGivenFirst(probsDual), maxSubstringLength1, true, minProductionProbability,0);
                    //EvaluateExamples(testingPairs, candidateWords, PSecondGivenFirst(probs)* InvertProbs( PSecondGivenFirst(probsDual) ), maxSubstringLength1, true, minProductionProbability*minProductionProbability);
                }

                //Reweight(probs, trainingTriples, exampleCounts, maxSubstringLength1);
            }

            Console.WriteLine("Finished."); Console.ReadLine();
        }

        public static double ProbSum(Dictionary<Pair<string, string>, double> probs)
        {
            double result = 0;
            foreach (KeyValuePair<Pair<string, string>, double> pair in probs)
                result += pair.Value;

            return result;
        }

        public static Dictionary<Pair<string, string>, double> PlusNSmoothing(Dictionary<Pair<string, string>, double> probs, double n)
        {
            Dictionary<Pair<string, string>, double> result = new Dictionary<Pair<string, string>, double>(probs.Count);
            foreach (KeyValuePair<Pair<string, string>, double> pair in probs)
                result[pair.Key] = pair.Value + n;

            return result;
        }

        public static void TestXMLData(List<KeyValuePair<string, string>> trainingPairs, List<KeyValuePair<string, string>> testingPairs, int maxSubstringLength1, int maxSubstringLength2)
        {
            bool capitalizeFirst = false;
            //Console
            int ngramSize = 4;
            //List<KeyValuePair<string, string>> trainingPairs = FilterExamplePairs(GetTaskPairs(trainingFile));
            
            if (capitalizeFirst) trainingPairs = UppercaseFirstSourceLetter(trainingPairs);

            List<Triple<string, string, double>> trainingTriples = ConvertExamples(trainingPairs);
            //List<KeyValuePair<string, string>> testingPairs = FilterExamplePairs(GetTaskPairs(testFile));

            if (capitalizeFirst) testingPairs = UppercaseFirstSourceLetter(testingPairs);

            List<string> trainingWords = new List<string>(trainingPairs.Count);
            foreach (KeyValuePair<string, string> pair in trainingPairs)
                trainingWords.Add(pair.Value);
            Dictionary<string, double> languageModel = new Dictionary<string, double>(); //GetNgramCounts(trainingPairs, maxSubstringLength1);
            Dictionaries.AddTo<string>(languageModel, GetNgramCounts(testingPairs, maxSubstringLength1),1); // null; // UniformLanguageModel(WikiTransliteration.GetNgramCounts(3, ngramSize, trainingWords, false));

            List<List<KeyValuePair<Pair<string, string>, double>>> exampleCounts;

            

            SparseDoubleVector<Pair<string, string>> probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, null, WeightingMode.None,NormalizationMode.None,false,out exampleCounts);
            //EvaluateExamples(testingPairs, PSecondGivenFirst(probs), maxSubstringLength1, languageModel, ngramSize, false);

            //EvaluateExamples(testingPairs, PSecondGivenFirst(probs), maxSubstringLength1, null, ngramSize, 20, false, 100);
            //EvaluateExamples(testingPairs, PSecondGivenFirst(probs), maxSubstringLength1, null, ngramSize, 20, false, 10);
            //EvaluateExamples(testingPairs, PSecondGivenFirst(probs), maxSubstringLength1, null, ngramSize, 20, true, 0);

            for (int i = 0; i < 20; i++)
            {
                //probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, PMutualProduction(probs), true,NormalizationMode.AllProductions, true, out exampleCounts);
                probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, (SparseDoubleVector<Pair<string,string>>)PSecondGivenFirst(probs)*0.5, WeightingMode.CountWeighted, NormalizationMode.None, true, out exampleCounts);
                //if (probs == null) break;
                //if (i>0) 

                Console.WriteLine("Iteration #" + i);
                //if (i > 2)
                {
                    //EvaluateExamples(testingPairs, PSecondGivenFirst(probs), maxSubstringLength1, null, ngramSize, 20, false, 200);
                    EvaluateExamples(testingPairs, (SparseDoubleVector<Pair<string, string>>)PSecondGivenFirst(probs)*0.5, maxSubstringLength1, null, ngramSize, 20, false, 100);
                    //EvaluateExamples(testingPairs, (SparseDoubleVector<Pair<string, string>>)PSecondGivenFirst(probs)*1, maxSubstringLength1, null, ngramSize, 20, false, 10);
                    EvaluateExamples(testingPairs, (SparseDoubleVector<Pair<string, string>>)PSecondGivenFirst(probs)*0.5, maxSubstringLength1, null, ngramSize, 20, true, 0);
                    //EvaluateExamples(testingPairs, LiftProbs(PSecondGivenFirst(probs)), maxSubstringLength1, 1, 0, false);
                    //EvaluateExamples(testingPairs, PSecondGivenFirst(probs), maxSubstringLength1, languageModel, ngramSize, true);
                }

                //Reweight(probs, trainingTriples, exampleCounts, maxSubstringLength1);
            }

            Console.WriteLine("Finished.");

            //Dictionary<string, Pair<string, double>> maxProbs = WikiTransliteration.GetMaxProbs(probs);

            //List<Triple<string, string, double>> weightedTrainingPairs = new List<Triple<string, string, double>>(trainingPairs.Count);
            //foreach (KeyValuePair<string, string> trainingPair in trainingPairs)
            //{
            //    int position = WikiTransliteration.Predict(20, trainingPair.Key, maxSubstringLength, maxProbs, new Dictionary<string, TopList<double, string>>()).IndexOfValue(trainingPair.Value);
            //    if (position >= 0) position++; else position = 20;
            //    weightedTrainingPairs.Add(new Triple<string, string, double>(trainingPair.Key, trainingPair.Value, position >= 0 ? ((double)1) / position : 0));
            //}

            //probs = MakeAlignmentTable(maxSubstringLength, weightedTrainingPairs, probs, true);

            
            //maxProbs = WikiTransliteration.GetMaxProbs(probs);
            //Map<string, string> probMap = WikiTransliteration.GetProbMap(probs);

            #region High-performing code
            //5977 words aligned.
            //5977 words aligned.
            //5977 words aligned.
            //943 pairs tested in total.
            //737 predictions contained (0.781548250265111)
            //586 predictions exactly correct (0.621420996818664)
            //MRR: 0.684359313795249

            //Dictionary<Pair<string, string>, double> probs = MakeAlignmentTable(maxSubstringLength, trainingPairs, null);
            //Dictionary<string, Pair<string, double>> maxProbs = WikiTransliteration.GetMaxProbs(probs);
            //Map<string, string> probMap = WikiTransliteration.GetProbMap(probs);

            //List<Triple<string, string, double>> weightedTrainingPairs = new List<Triple<string, string, double>>(trainingPairs.Count);
            ////foreach (KeyValuePair<string,string> trainingPair in trainingPairs)
            ////    weightedTrainingPairs.Add(new Triple<string, string, double>(trainingPair.Key, trainingPair.Value, Math.Pow(WikiTransliteration.GetAlignmentProbability(trainingPair.Key, trainingPair.Value, maxSubstringLength, probs, 0), ((double)1)/trainingPair.Value.Length ) ));

            //foreach (KeyValuePair<string, string> trainingPair in trainingPairs)
            //{
            //    int position = WikiTransliteration.Predict(20, trainingPair.Key, maxSubstringLength, maxProbs, new Dictionary<string, TopList<double, string>>()).IndexOfValue(trainingPair.Value);
            //    if (position >= 0) position++; else position = 20;
            //    weightedTrainingPairs.Add(new Triple<string, string, double>(trainingPair.Key, trainingPair.Value, position >= 0 ? ((double)1)/position : 0));
            //}

            //probs = MakeAlignmentTable(maxSubstringLength, weightedTrainingPairs, null);
            //probs = MakeAlignmentTable(maxSubstringLength, weightedTrainingPairs, probs);
            #endregion
            

            Console.ReadLine();
        }

        public static void TestXMLDataWithContext(string trainingFile, string testFile, int maxSubstringLength, int contextSize, bool fallback)
        {
            bool capitalizeFirst = false;

            List<KeyValuePair<string, string>> trainingPairs = FilterExamplePairs(GetTaskPairs(trainingFile));

            if (capitalizeFirst) trainingPairs = UppercaseFirstSourceLetter(trainingPairs);

            List<Triple<string, string, double>> trainingTriples = ConvertExamples(trainingPairs);
            List<KeyValuePair<string, string>> testingPairs = FilterExamplePairs(GetTaskPairs(testFile));

            if (capitalizeFirst) testingPairs = UppercaseFirstSourceLetter(testingPairs);

            List<string> trainingWords = new List<string>(trainingPairs.Count);
            foreach (KeyValuePair<string, string> pair in trainingPairs)
                trainingWords.Add(pair.Value);            

            List<List<KeyValuePair<Triple<string, string, string>, double>>> exampleCounts;

            Dictionary<Triple<string, string, string>, double> probs = MakeRawAlignmentTableWithContext(maxSubstringLength, maxSubstringLength, trainingTriples, null, contextSize, fallback, false, NormalizationMode.BySourceSubstring, false, out exampleCounts); 
            EvaluateExamples(testingPairs, Dictionaries.Multiply<Triple<string,string,string>>(PSecondGivenFirst(probs),PFirstGivenSecond(probs)), maxSubstringLength,1,contextSize,fallback);

            for (int i = 1; i < 50; i++)
            {
                //probs = MakeRawAlignmentTableWithContext(maxSubstringLength, maxSubstringLength, trainingTriples, Dictionaries.Multiply<Triple<string, string, string>>(PSecondGivenFirst(probs), PFirstGivenSecond(probs)), contextSize, fallback, false, NormalizationMode.BySourceSubstring, false, out exampleCounts);
                probs = MakeRawAlignmentTableWithContext(maxSubstringLength, maxSubstringLength, trainingTriples, (probs), contextSize, fallback, false, NormalizationMode.BySourceSubstring, false, out exampleCounts);

                Console.WriteLine("Iteration #" + i);
                //EvaluateExamples(testingPairs, Dictionaries.Multiply<Triple<string, string, string>>(PSecondGivenFirst(probs), PFirstGivenSecond(probs)), maxSubstringLength, 1, contextSize, fallback);
                EvaluateExamples(testingPairs, PSecondGivenFirst(probs), maxSubstringLength, 1, contextSize, fallback);
            }

            Console.WriteLine("Finished.");

            Console.ReadLine();
        }


        private static string PositionalizeString(string word)
        {
            char[] chars = new char[word.Length];
            for (int i = 0; i < word.Length; i++)
                chars[i] = (char)((int)word[i] + (i << 8));

            return new string(chars);
        }

        public static List<KeyValuePair<string, string>> UppercaseFirstSourceLetter(List<KeyValuePair<string, string>> pairs)
        {
            List<KeyValuePair<string, string>> result = new List<KeyValuePair<string, string>>(pairs.Count);

            for (int i = 0; i < pairs.Count; i++)
                result.Add( new KeyValuePair<string, string>(Char.ToUpper(pairs[i].Key[0]) + pairs[i].Key.Substring(1), pairs[i].Value) );
                //result.Add(new KeyValuePair<string, string>(PositionalizeString(pairs[i].Key), pairs[i].Value));

            return result;
        }

        public static string NormalizeHebrew(string word)
        {
            word = word.Replace('ן','נ');
            word = word.Replace('ך', 'כ');
            word = word.Replace('ץ', 'צ');
            word = word.Replace('ם','מ');
            word = word.Replace('ף', 'פ');

            return word;
        }

        public static List<KeyValuePair<string, string>> NormalizeHebrew(List<KeyValuePair<string, string>> pairs)
        {
            List<KeyValuePair<string, string>> result = new List<KeyValuePair<string, string>>(pairs.Count);

            for (int i = 0; i < pairs.Count; i++)
                result.Add(new KeyValuePair<string, string>(pairs[i].Key, NormalizeHebrew( pairs[i].Value ) ));
            //result.Add(new KeyValuePair<string, string>(PositionalizeString(pairs[i].Key), pairs[i].Value));

            return result;
        }

        public static List<KeyValuePair<string, string>> UnderscoreSurround(List<KeyValuePair<string, string>> pairs)
        {
            List<KeyValuePair<string, string>> result = new List<KeyValuePair<string, string>>(pairs.Count);

            for (int i = 0; i < pairs.Count; i++)
                result.Add(new KeyValuePair<string, string>(Char.ToUpper(pairs[i].Key[0]) + pairs[i].Key.Substring(1, pairs[i].Key.Length - 2) + ((char)(pairs[i].Key[pairs[i].Key.Length - 1]+ ((char)500))), pairs[i].Value));
            //result.Add(new KeyValuePair<string, string>(PositionalizeString(pairs[i].Key), pairs[i].Value));

            return result;
        }

        public static void TestXMLDataOldOldStyleForTask(string trainingFile, string secondTrainingFile, string testFile, int maxSubstringLength1, int maxSubstringLength2)
        {
            //Console
            int ngramSize = 3;
            List<KeyValuePair<string, string>> trainingPairs = (FilterExamplePairs(GetTaskPairs(trainingFile)));
            List<string> testingWords = null;
            List<KeyValuePair<string, string>> testingPairs = null;

            if (secondTrainingFile != null)
            {
                trainingPairs.AddRange(FilterExamplePairs(GetTaskPairs(secondTrainingFile)));
                testingWords = FilterExampleWords(GetTaskWords(testFile));
            }
            else
            {
                testingPairs = (FilterExamplePairs(GetTaskPairs(testFile)));
            }

            List<Triple<string, string, double>> trainingTriples = ConvertExamples(trainingPairs);

            List<string> trainingWords = new List<string>(trainingPairs.Count);
            foreach (KeyValuePair<string, string> pair in trainingPairs)
                trainingWords.Add(pair.Value);
            Dictionary<string, double> languageModel = WikiTransliteration.GetNgramProbs(1, ngramSize, trainingWords);

            List<List<KeyValuePair<Pair<string, string>, double>>> exampleCounts;

            Dictionary<Pair<string, string>, double> probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, null, WeightingMode.None, NormalizationMode.AllProductions, false, out exampleCounts);
            //EvaluateExamples(testingPairs, PMutualProduction(probs), maxSubstringLength1, null, ngramSize, 20, false);
            //EvaluateExamples(testingPairs, PMutualProduction(probs), maxSubstringLength1, languageModel, ngramSize, 200, true);

            for (int i = 0; i < 500; i++)
            {
                //probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, PMutualProduction(probs), true,NormalizationMode.AllProductions, true, out exampleCounts);
                probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, PMutualProduction(probs), WeightingMode.FindWeighted, NormalizationMode.AllProductions, false, out exampleCounts);
                if (probs == null) break;

                if (i == 4)
                {
                    EvaluateExamplesToFile(testingWords, PMutualProduction(probs), maxSubstringLength1, languageModel, ngramSize, 10, true, @"C:\Data\WikiTransliteration\enChTaskPredictions.xml");
                    return;
                }
                
                //Reweight(probs, trainingTriples, exampleCounts, maxSubstringLength1);
            }

            Console.WriteLine("Finished.");

            Console.ReadLine();
        }

        public static void TestXMLDataNewStyle(string trainingFile, string secondTrainingFile, string testFile, int maxSubstringLength1, int maxSubstringLength2)
        {
            //Console
            int ngramSize = 3;
            List<KeyValuePair<string, string>> trainingPairs = (FilterExamplePairs(GetTaskPairs(trainingFile)));
            List<string> testingWords = null;            
            List<KeyValuePair<string, string>> testingPairs = null;

            if (secondTrainingFile != null)
            {
                trainingPairs.AddRange(FilterExamplePairs(GetTaskPairs(secondTrainingFile)));
                testingWords = FilterExampleWords( GetTaskWords(testFile) );
            }
            else
            {                
                testingPairs = (FilterExamplePairs(GetTaskPairs(testFile)));
            }

            List<Triple<string, string, double>> trainingTriples = ConvertExamples(trainingPairs);

            List<string> trainingWords = new List<string>(trainingPairs.Count);
            foreach (KeyValuePair<string, string> pair in trainingPairs)
                trainingWords.Add(pair.Value);
            Dictionary<string, double> languageModel = null; // UniformLanguageModel(WikiTransliteration.GetNgramCounts(3, ngramSize, trainingWords, false));

            List<List<KeyValuePair<Pair<string, string>, double>>> exampleCounts;

            Dictionary<Pair<string, string>, double> probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, null, WeightingMode.None, NormalizationMode.None, false, out exampleCounts);
            //EvaluateExamples(testingPairs, PSecondGivenFirst(probs), maxSubstringLength1, languageModel, ngramSize, false);

            for (int i = 0; i < 500; i++)
            {
                //probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, PMutualProduction(probs), true,NormalizationMode.AllProductions, true, out exampleCounts);
                probs = MakeRawAlignmentTable(maxSubstringLength1, maxSubstringLength2, trainingTriples, PSecondGivenFirst(probs), WeightingMode.CountWeighted, NormalizationMode.None, true, out exampleCounts);
                if (probs == null) break;
                //if (i>0) 

                Console.WriteLine("Iteration #" + i);
                //if (i > 2)
                {
                    //EvaluateExamples(testingPairs, PSecondGivenFirst(probs), maxSubstringLength1, null, ngramSize, 20, false);
                    //EvaluateExamples(testingPairs, PSecondGivenFirst(probs), maxSubstringLength1, languageModel, ngramSize, true);
                }

                if (i == 5)
                {
                    EvaluateExamplesToFile(testingWords, PSecondGivenFirst(probs), maxSubstringLength1, null, ngramSize, 10, true, @"C:\Data\WikiTransliteration\enRuTaskPredictions.xml");
                    return;
                }
                //Reweight(probs, trainingTriples, exampleCounts, maxSubstringLength1);
            }

            Console.WriteLine("Finished.");

            Console.ReadLine();
        }


        public static void TestXMLDataLog(string trainingFile, string testFile, int maxSubstringLength1, int maxSubstringLength2)
        {
            List<KeyValuePair<string, string>> trainingPairs = GetTaskPairs(trainingFile);
            List<KeyValuePair<string, string>> testingPairs = GetTaskPairs(testFile);

            Dictionary<Pair<string, string>, double> probs = MakeAlignmentTableLog(maxSubstringLength1, maxSubstringLength2, trainingPairs, null, false);
            EvaluateExamplesLog(testingPairs, probs, maxSubstringLength1, true);

            for (int i = 0; i < 30; i++)
            {
                probs = MakeAlignmentTableLog(maxSubstringLength1, maxSubstringLength2, trainingPairs, probs, true);
                if (probs == null) break;
                EvaluateExamplesLog(testingPairs, probs, maxSubstringLength1, true);
            }

            Console.WriteLine("Finished.");

            Console.ReadLine();
        }

        private static void EvaluateExamplesLog(List<KeyValuePair<string, string>> testingPairs, Dictionary<Pair<string, string>, double> probs, int maxSubstringLength, bool tryAllPredictions)
        {
            //Dictionary<string, Pair<string, double>> maxProbs = tryAllPredictions ? null : WikiTransliteration.GetMaxProbs(probs);
            Map<string, string> probMap = tryAllPredictions ? WikiTransliteration.GetProbMap(probs) : null;

            int correct = 0;
            int contained = 0;
            double mrr = 0;
            foreach (KeyValuePair<string, string> pair in testingPairs)
            {
                TopList<double, string> predictions =
                        WikiTransliteration.PredictLog(20, pair.Key, maxSubstringLength, probMap, probs, new Dictionary<string, TopList<double, string>>(), null, 0);                        

                int position = predictions.Values.IndexOf(pair.Value);
                if (position == 0)
                    correct++;

                if (position >= 0)
                    contained++;

                if (position < 0)
                    position = 20;

                mrr += 1 / ((double)position + 1);
            }

            mrr /= testingPairs.Count;

            Console.WriteLine(testingPairs.Count + " pairs tested in total.");
            Console.WriteLine(contained + " predictions contained (" + (((double)contained) / testingPairs.Count) + ")");
            Console.WriteLine(correct + " predictions exactly correct (" + (((double)correct) / testingPairs.Count) + ")");
            Console.WriteLine("MRR: " + mrr);
        }

        private static void WriteTaskResultsHeader(StreamWriter s, string targetLanguage, string runType, string runID, string comments)
        {
            s.WriteLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            s.WriteLine("<TransliterationTaskResults");
            s.WriteLine("SourceLang = \"English\"");
            s.WriteLine("TargetLang = \"" + targetLanguage + "\"");
            s.WriteLine("GroupID = \"Jeff Pasternack, University of Illinois, Urbana-Champaign\"");
            s.WriteLine("RunID = \"" + runID + "\"");
            s.WriteLine("RunType = \"" + runType + "\"");
            s.WriteLine("Comments = \"" + comments + "\">");
            
        }

        private static void WriteTaskResults(StreamWriter s, int index, string sourceWord, IList<string> predictions)
        {
            s.WriteLine("<Name ID=\""+ (index+1) +"\">");
            s.WriteLine("<SourceName>"+sourceWord+"</SourceName>");
            for (int i = 0; i < predictions.Count && i < 10; i++)
            {
                s.WriteLine("<TargetName ID=\"" + (i+1) + "\">" + predictions[i] + "</TargetName>");                
            }

            s.WriteLine("</Name>");
        }

        private static void WriteTaskResultsFooter(StreamWriter s)
        {
            s.WriteLine("</TransliterationTaskResults>");
        }

        private static void EvaluateExamplesToFile(List<string> testWords, Dictionary<Pair<string, string>, double> probs, int maxSubstringLength, Dictionary<string, double> languageModel, int ngramSize, int predictionCount, bool tryAllPredictions, string resultsFile)
        {
            Dictionary<string, Pair<string, double>> maxProbs = tryAllPredictions ? null : WikiTransliteration.GetMaxProbs(probs);
            Map<string, string> probMap = tryAllPredictions ? WikiTransliteration.GetProbMap(probs) : null;

            StreamWriter writer = null;

            writer = new StreamWriter(resultsFile, false, Encoding.UTF8);
            WriteTaskResultsHeader(writer, "Russian", "standard", "1", "EM-alignments with no normalization");

            int index = 0;

            foreach (string testWord2 in testWords)
            {
                string testWord = testWord2;
                bool retry = false;

                TopList<double, string> predictions;

                do
                {
                    predictions =
                        tryAllPredictions ?
                        WikiTransliteration.Predict((languageModel == null ? predictionCount : 20), testWord, maxSubstringLength, probMap, probs, new Dictionary<string, TopList<double, string>>(), null, 0)
                            :
                            WikiTransliteration.Predict((languageModel == null ? predictionCount : 20), testWord, maxSubstringLength, maxProbs, new Dictionary<string, TopList<double, string>>());

                    if (languageModel != null)
                    {
                        TopList<double, string> fPredictions = new TopList<double, string>(predictionCount);
                        foreach (KeyValuePair<double, string> prediction in predictions)
                            //fPredictions.Add(prediction.Key * Math.Pow(WikiTransliteration.GetLanguageProbabilityViterbi(prediction.Value, languageModel, ngramSize),1d/(prediction.Value.Length)), prediction.Value);
                            fPredictions.Add(prediction.Key * Math.Pow(WikiTransliteration.GetLanguageProbability(prediction.Value, languageModel, ngramSize), 1), prediction.Value);
                        predictions = fPredictions;
                    }

                    if (predictions.Count == 0)
                    {
                        if (!retry)
                        {
                            retry = true;
                            testWord = StripAccent(testWord);
                        }
                        else
                        {
                            retry = false;
                            predictions.Add(1, "Unknown");
                        }
                    }
                    else
                        retry = false;
                }
                while (retry);

                WriteTaskResults(writer, index++, testWord2, predictions.Values);
            }

            
                

            WriteTaskResultsFooter(writer);
            writer.Close();
        }

        public static Dictionary<Pair<string, string>, double> PruneProbs(int topK, Dictionary<Pair<string, string>, double> probs)
        {
            Dictionary<string, List<Pair<string, double>>> lists = new Dictionary<string, List<Pair<string, double>>>();
            foreach (KeyValuePair<Pair<string, string>, double> pair in probs)
            {
                if (!lists.ContainsKey(pair.Key.x))
                    lists[pair.Key.x] = new List<Pair<string, double>>();

                lists[pair.Key.x].Add(new Pair<string, double>(pair.Key.y, pair.Value));
            }

            Dictionary<Pair<string, string>, double> result = new Dictionary<Pair<string, string>, double>();
            foreach (KeyValuePair<string, List<Pair<string, double>>> pair in lists)
            {
                pair.Value.Sort(new Comparison<Pair<string,double>>( delegate(Pair<string, double> x, Pair<string, double> y) { return Math.Sign(y.y - x.y); } ));
                int toAdd = Math.Min(topK, pair.Value.Count);
                for (int i = 0; i < toAdd; i++)
                    result.Add(new Pair<string, string>(pair.Key, pair.Value[i].x), pair.Value[i].y);
            }

            return result;
        }

        public static TopList<double, string> TopProbs(Dictionary<Pair<string, string>, double> probs, string sourceSubstring)
        {
            TopList<double, string> topList = new TopList<double, string>(20);
            foreach (KeyValuePair<Pair<string, string>, double> pair in probs)
                if (pair.Key.x == sourceSubstring) topList.Add(pair.Value, pair.Key.y);

            return topList;

            ////Console.WriteLine();

            ////foreach (KeyValuePair<double, string> pair in topList)
            ////    Console.WriteLine(pair.Value + "\t" + pair.Key);

            ////Console.WriteLine();
        }

        private static void EvaluateExamplesDual(Dictionary<string, List<string>> testingPairs, List<string> candidates, Dictionary<Pair<string, string>, double> probs, Dictionary<Pair<string, string>, double> probsDual, int maxSubstringLength, bool summedPredications, double minProductionProbability, int maxRank)
        {
            int correct = 0;
            //int contained = 0;
            double mrr = 0;
            int misses = 0;

            foreach (KeyValuePair<string, List<string>> pair in testingPairs)
            {
                int index = 0;

                string[] words;
                if (false) //File.Exists(@"C:\Data\WikiTransliteration\RWords\" + pair.Key + ".txt"))
                {
                    List<string> readWords = new List<string>();
                    foreach (string line in File.ReadAllLines(@"C:\Data\WikiTransliteration\RWords\" + pair.Key + ".txt"))
                    {
                        readWords.Add(line.Split('\t')[0]);
                        if (readWords.Count == 50) break;
                    }

                    words = readWords.ToArray();
                }
                else
                    words = candidates.ToArray(); //new string[candidates.Count];

                if (maxRank == int.MaxValue || maxRank <= 0)
                {
                    double[] scores = new double[words.Length];
                    double[] scores2 = new double[words.Length];                    

                    for (int i = 0; i < words.Length; i++)
                    {
                        scores[i] = Math.Log(summedPredications ?
                            WikiTransliteration.GetSummedAlignmentProbability(pair.Key, words[i], maxSubstringLength, maxSubstringLength, probs, new Dictionary<Pair<string, string>, double>(), minProductionProbability)
                            / segSums[pair.Key.Length - 1][words[i].Length - 1]
                            : WikiTransliteration.GetAlignmentProbability(pair.Key, words[i], maxSubstringLength, probs, 0, minProductionProbability))
                        + Math.Log(summedPredications ?
                            WikiTransliteration.GetSummedAlignmentProbability(words[i], pair.Key, maxSubstringLength, maxSubstringLength, probsDual, new Dictionary<Pair<string, string>, double>(), minProductionProbability)
                            / segSums[pair.Key.Length - 1][words[i].Length - 1]
                            : WikiTransliteration.GetAlignmentProbability(words[i], pair.Key, maxSubstringLength, probsDual, 0, minProductionProbability));
                    }

                    Array.Sort<double, string>(scores, words);

                    //write to disk
                    //try
                    //{
                    //    StreamWriter w = new StreamWriter(@"C:\Data\WikiTransliteration\RWords\" + pair.Key + ".txt");
                    //    for (int i = words.Length - 1; i >= 0; i--)
                    //        w.WriteLine(words[i] + "\t" + scores[i]);
                    //    w.Close();
                    //}
                    //catch { }

                    for (int i = words.Length - 1; i >= 0; i--)
                        if (pair.Value.Contains(words[i]))
                        {
                            index = i; break;
                        }

                    index = words.Length - index;
                }
                else
                {
                    TopList<double, string> results = new TopList<double, string>(maxRank);

                    for (int i = 0; i < words.Length; i++)
                    {
                        double score = Math.Log(summedPredications ?
                                WikiTransliteration.GetSummedAlignmentProbability(pair.Key, words[i], maxSubstringLength, maxSubstringLength, probs, new Dictionary<Pair<string, string>, double>(), minProductionProbability)
                                : WikiTransliteration.GetAlignmentProbability(pair.Key, words[i], maxSubstringLength, probs, results.Count < maxRank ? 0 : Math.Exp(results[maxRank - 1].Key), minProductionProbability));
                        if (results.Count == maxRank && results[maxRank - 1].Key >= score) continue;
                        score += Math.Log(summedPredications ?
                                WikiTransliteration.GetSummedAlignmentProbability(words[i], pair.Key, maxSubstringLength, maxSubstringLength, probsDual, new Dictionary<Pair<string, string>, double>(), minProductionProbability)
                                : WikiTransliteration.GetAlignmentProbability(words[i], pair.Key, maxSubstringLength, probsDual, results.Count < maxRank ? 0 : Math.Exp(results[maxRank - 1].Key - score), minProductionProbability));

                        results.Add(score, words[i]);
                    }

                    index = maxRank + 2;
                    for (int i = 0; i < results.Count; i++)
                        if (pair.Value.Contains(results[i].Value))
                        {
                            index = i+1; break;
                        }
                }

                if (index == 1)
                    correct++;
                else
                    misses++;
                mrr += ((double)1) / index;
            }

            mrr /= testingPairs.Count;

            Console.WriteLine(testingPairs.Count + " pairs tested in total; " + candidates.Count + " candidates.");
            //Console.WriteLine(contained + " predictions contained (" + (((double)contained) / testingPairs.Count) + ")");
            Console.WriteLine(correct + " predictions exactly correct (" + (((double)correct) / testingPairs.Count) + ")");
            Console.WriteLine("MRR: " + mrr);            
        }

        private static void EvaluateExamplesDiscoveryGeneration(Dictionary<string, List<string>> testingPairs, List<string> candidates, Dictionary<Pair<string, string>, double> probs, int maxSubstringLength, bool summedPredications, int generationPruning)
        {
            int correct = 0;
            //int contained = 0;
            double mrr = 0;
            int misses = 0;

            Map<string,string> probMap = WikiTransliteration.GetProbMap(probs);
            Map<string, string> candidateMap = new Map<string, string>();
            foreach (string candidate in candidates)
            {
                for (int i = 1; i <= candidate.Length; i++)
                    candidateMap.Add(candidate.Substring(0, i), candidate);
            }

            foreach (KeyValuePair<string, List<string>> pair in testingPairs)
            {
                TopList<double,string> topList;

                if (summedPredications)
                    topList = WikiTransliteration.Predict2(generationPruning, pair.Key, maxSubstringLength, probMap, probs, new Dictionary<string,Dictionary<string,double>>(), generationPruning);
                else
                    topList = WikiTransliteration.Predict(generationPruning, pair.Key, maxSubstringLength, probMap, probs, new Dictionary<string, TopList<double, string>>(), null, 0);                

                int index = int.MaxValue;

                for (int i = 0; i < topList.Count; i++)
                {
                    for (int j = topList[i].Value.Length; j>=3; j--)
                    {
                        string ss = topList[i].Value.Substring(0,j);
                        if (candidateMap.ContainsKey(ss))
                        {
                            if (pair.Value.Contains(candidateMap.GetValuesForKey(ss)[0]))
                            {
                                index = i + 1;
                                break;
                            }
                        }
                    }

                    if (index < int.MaxValue) break; //done
                }                                        

                if (index == 1)
                    correct++;
                else
                    misses++;

                mrr += (index == int.MaxValue ? 0 : ((double)1) / index);
            }

            mrr /= testingPairs.Count;

            Console.WriteLine(testingPairs.Count + " pairs tested in total; " + candidates.Count + " candidates.");
            //Console.WriteLine(contained + " predictions contained (" + (((double)contained) / testingPairs.Count) + ")");
            Console.WriteLine(correct + " predictions exactly correct (" + (((double)correct) / testingPairs.Count) + ")");
            Console.WriteLine("MRR: " + mrr);
        }

        private static void DiscoveryEvaluation(Dictionary<string, List<string>> testingPairs, List<string> candidates, TransliterationModel model)
        {
            int correct = 0;
            //int contained = 0;
            double mrr = 0;
            int misses = 0;

            foreach (KeyValuePair<string, List<string>> pair in testingPairs)
            {
                double[] scores = new double[candidates.Count];
                string[] words = candidates.ToArray(); //new string[candidates.Count];

                for (int i = 0; i < words.Length; i++)                
                    scores[i] = model.GetProbability(pair.Key, words[i]);                

                Array.Sort<double, string>(scores, words);

                int index = 0;
                for (int i = words.Length - 1; i >= 0; i--)
                    if (pair.Value.Contains(words[i]))
                    {
                        index = i; break;
                    }

                index = words.Length - index;

                if (index == 1)
                    correct++;
                else
                    misses++;
                mrr += ((double)1) / index;
            }

            mrr /= testingPairs.Count;

            Console.WriteLine(testingPairs.Count + " pairs tested in total; " + candidates.Count + " candidates.");
            //Console.WriteLine(contained + " predictions contained (" + (((double)contained) / testingPairs.Count) + ")");
            Console.WriteLine(correct + " predictions exactly correct (" + (((double)correct) / testingPairs.Count) + ")");
            Console.WriteLine("MRR: " + mrr);
        }

        private static void EvaluateExamples(Dictionary<string,List<string>> testingPairs, List<string> candidates, Dictionary<Pair<string, string>, double> probs, int maxSubstringLength, bool summedPredications, double minProductionProbability)
        {
            int correct = 0;
            //int contained = 0;
            double mrr = 0;
            int misses = 0;

            foreach (KeyValuePair<string, List<string>> pair in testingPairs)
            {
                double[] scores = new double[candidates.Count];
                string[] words = candidates.ToArray(); //new string[candidates.Count];

                for (int i = 0; i < words.Length; i++)
                {
                    scores[i] = (summedPredications ?
                        WikiTransliteration.GetSummedAlignmentProbability(pair.Key, words[i], maxSubstringLength, maxSubstringLength, probs, new Dictionary<Pair<string, string>, double>(), minProductionProbability) 
                            / segSums[pair.Key.Length-1][words[i].Length-1]
                        : WikiTransliteration.GetAlignmentProbability(pair.Key, words[i], maxSubstringLength, probs, 0, minProductionProbability));
                }

                Array.Sort<double, string>(scores, words);

                int index = 0;
                for (int i = words.Length - 1; i >= 0; i--)
                    if (pair.Value.Contains(words[i]))
                    {
                        index = i; break;
                    }

                index = words.Length - index;
                
                if (index == 1)
                    correct++;
                else
                    misses++;
                mrr += ((double)1) / index;
            }

            mrr /= testingPairs.Count;

            Console.WriteLine(testingPairs.Count + " pairs tested in total; " + candidates.Count + " candidates.");
            //Console.WriteLine(contained + " predictions contained (" + (((double)contained) / testingPairs.Count) + ")");
            Console.WriteLine(correct + " predictions exactly correct (" + (((double)correct) / testingPairs.Count) + ")");
            Console.WriteLine("MRR: " + mrr);
        }

        private static void EvaluateExamples(List<KeyValuePair<string, string>> testingPairs, Dictionary<Pair<string, string>, double> probs, int maxSubstringLength, Dictionary<string, double> languageModel, int ngramSize, int predictionCount, bool tryAllPredictions, int summedProbPredictionsPruningSize)
        {
            if (summedProbPredictionsPruningSize > 0)
            {
                probs = PruneProbs(summedProbPredictionsPruningSize, probs);
            }

            Dictionary<string, Pair<string, double>> maxProbs = tryAllPredictions ? null : WikiTransliteration.GetMaxProbs(probs);
            Map<string, string> probMap = (summedProbPredictionsPruningSize > 0 || tryAllPredictions) ? WikiTransliteration.GetProbMap(probs) : null;            

            int correct = 0;
            int contained = 0;
            double mrr = 0;
            
            foreach (KeyValuePair<string, string> pair in testingPairs)
            {
                TopList<double, string> predictions;

                if (summedProbPredictionsPruningSize > 0)
                    predictions = WikiTransliteration.Predict2(predictionCount, pair.Key, maxSubstringLength, probMap, probs, new Dictionary<string, Dictionary<string, double>>(), summedProbPredictionsPruningSize);
                else
                    predictions =
                        tryAllPredictions ?
                            WikiTransliteration.Predict(predictionCount, pair.Key, maxSubstringLength, probMap, probs, new Dictionary<string, TopList<double, string>>(), null, 0)
                            :
                            WikiTransliteration.Predict(predictionCount, pair.Key, maxSubstringLength, maxProbs, new Dictionary<string, TopList<double, string>>());

                if (languageModel != null)
                {
                    TopList<double, string> fPredictions = new TopList<double, string>(20);
                    foreach (KeyValuePair<double, string> prediction in predictions)
                        //fPredictions.Add(prediction.Key * Math.Pow(WikiTransliteration.GetLanguageProbabilityViterbi(prediction.Value, languageModel, ngramSize),1d/(prediction.Value.Length)), prediction.Value);
                        fPredictions.Add(prediction.Key * Math.Pow(WikiTransliteration.GetLanguageProbability(prediction.Value, languageModel, ngramSize), 1), prediction.Value);
                    predictions = fPredictions;
                }
                
                

                int position = predictions.Values.IndexOf(pair.Value);
                if (position == 0)
                    correct++;

                if (position >= 0)
                    contained++;

                if (position < 0)
                    position = 20;

                mrr += 1 / ((double)position + 1);
            }

            mrr /= testingPairs.Count;

            Console.WriteLine(testingPairs.Count + " pairs tested in total.");
            Console.WriteLine(contained + " predictions contained (" + (((double)contained) / testingPairs.Count) + ")");
            Console.WriteLine(correct + " predictions exactly correct (" + (((double)correct) / testingPairs.Count) + ")");
            Console.WriteLine("MRR: " + mrr);
        }


        public static Dictionary<Triple<string, string, string>, double> LiftProbs(Dictionary<Pair<string, string>, double> probs)
        {
            Dictionary<Triple<string, string, string>, double> result = new Dictionary<Triple<string, string, string>, double>(probs.Count);
            foreach (KeyValuePair<Pair<string, string>, double> pair in probs)
                result.Add(new Triple<string, string, string>("", pair.Key.x, pair.Key.y), pair.Value);

            return result;
        }

        private static void EvaluateExamples(List<KeyValuePair<string, string>> testingPairs, Dictionary<Triple<string, string, string>, double> probs, int maxSubstringLength, int predictionCount, int contextSize, bool fallback)
        {
            Map<Pair<string,string>, string> probMap = WikiTransliteration.GetProbMap(probs);

            int correct = 0;
            int contained = 0;
            double mrr = 0;

            foreach (KeyValuePair<string, string> pair in testingPairs)
            {
                TopList<double, string> predictions =                    
                        WikiTransliteration.PredictViterbi(predictionCount, contextSize, fallback, pair.Key, maxSubstringLength, probMap, probs);

                int position = predictions.Values.IndexOf(pair.Value);
                if (position == 0)
                    correct++;

                if (position >= 0)
                    contained++;

                mrr += position >= 0 ? 1 / ((double)position + 1) : 0;
            }

            mrr /= testingPairs.Count;

            Console.WriteLine(testingPairs.Count + " pairs tested in total.");
            Console.WriteLine(contained + " predictions contained (" + (((double)contained) / testingPairs.Count) + ")");
            Console.WriteLine(correct + " predictions exactly correct (" + (((double)correct) / testingPairs.Count) + ")");
            Console.WriteLine("MRR: " + mrr);
        }

        private static void MakeAlignmentTableEM(string translationMapFile, string alignmentFile, int maxSubstringLength, int iterations)
        {
            Pasternack.Collections.Generic.Specialized.InternDictionary<string> internTable = new Pasternack.Collections.Generic.Specialized.InternDictionary<string>();
            List<Triple<string, string, WordAlignment>> examples = GetTrainingExamples(translationMapFile);
            List<Triple<string, string, double>> examples2 = new List<Triple<string, string, double>>(examples.Count);
            foreach (Triple<string, string, WordAlignment> triple in examples)
                examples2.Add(new Triple<string, string, double>(triple.x, triple.y, 1));

            Dictionary<Pair<string, string>, double> probs = MakeAlignmentTable(maxSubstringLength,maxSubstringLength, examples2, null,false);
            
            for (int i = 0; i < iterations; i++)
                probs = MakeAlignmentTable(maxSubstringLength, maxSubstringLength, examples2, probs,true);

            //Console.WriteLine(alignmentCount + " words aligned.");



            WriteProbDictionary(alignmentFile, probs);

            //FileStream fs = File.Create(alignmentFile);
            //System.Runtime.Serialization.Formatters.Binary.BinaryFormatter bf = new System.Runtime.Serialization.Formatters.Binary.BinaryFormatter();
            ////bf.Serialize(fs, counts);
            ////bf.Serialize(fs, totals);
            //bf.Serialize(fs, probs);
            //fs.Close();
        }

        //private static void MakeAlignmentTable(string translationMapFile, string alignmentFile, int maxSubstringLength, bool oneAlignmentPerWord)
        //{
        //    Pasternack.Collections.Generic.Specialized.InternDictionary<string> internTable = new Pasternack.Collections.Generic.Specialized.InternDictionary<string>();

        //    Dictionary<Pasternack.Utility.Pair<string, string>, int> weights;
        //    Map<string, string> translationMap = ReadTranslationMap(@"C:\Data\WikiTransliteration\enRuTranslationMap.dat", out weights);
        //    Dictionary<Pair<string, string>, double> counts = new Dictionary<Pair<string, string>, double>();

        //    int alignmentCount = 0;

        //    foreach (string sourceWord in translationMap.Keys)
        //    {
        //        int maxWeight = 0; string bestWord = null;
        //        foreach (string targetWord in translationMap.GetValuesForKey(sourceWord))
        //            if (weights[new Pair<string, string>(sourceWord, targetWord)] > maxWeight)
        //            {
        //                maxWeight = weights[new Pair<string, string>(sourceWord, targetWord)];
        //                bestWord = targetWord;
        //            }

        //        if (maxWeight >= 100 && sourceWord.Length * maxSubstringLength >= bestWord.Length && bestWord.Length*maxSubstringLength >= sourceWord.Length)
        //        {
        //            if (sourceWord.Contains("ashcroft"))
        //                Console.WriteLine("KLEIN!");

        //            alignmentCount++;
        //            Dictionaries.AddTo<Pair<string, string>>(counts, oneAlignmentPerWord ? WikiTransliteration.FindAlignments(sourceWord, bestWord, maxSubstringLength, internTable) : WikiTransliteration.CountAlignments(sourceWord, bestWord, maxSubstringLength, internTable), 1);
        //        }
        //    }

        //    Console.WriteLine(alignmentCount + " words aligned.");

        //    Dictionary<string, double> totals = WikiTransliteration.GetAlignmentTotals1(counts);
        //    Dictionary<Pair<string, string>, double> probs = new Dictionary<Pair<string, string>, double>(counts.Count);
        //    foreach (KeyValuePair<Pair<string, string>, double> pair in counts)
        //        probs[pair.Key] = pair.Value / totals[pair.Key.x];

        //    WriteProbDictionary(alignmentFile, probs);

        //    //FileStream fs = File.Create(alignmentFile);
        //    //System.Runtime.Serialization.Formatters.Binary.BinaryFormatter bf = new System.Runtime.Serialization.Formatters.Binary.BinaryFormatter();
        //    ////bf.Serialize(fs, counts);
        //    ////bf.Serialize(fs, totals);
        //    //bf.Serialize(fs, probs);
        //    //fs.Close();
        //}

        public static void WriteProbDictionary(string filename, Dictionary<Pair<string,string>,double> dictionary)
        {
            BinaryWriter writer = new BinaryWriter(File.Create(filename));

            writer.Write(dictionary.Count);
            foreach (KeyValuePair<Pair<string, string>, double> pair in dictionary)
            {
                writer.Write(pair.Key.x); writer.Write(pair.Key.y);
                writer.Write(pair.Value);
            }
            writer.Close();
        }

        public static Dictionary<Pair<string, string>, double> ReadProbDictionary(string filename)
        {
            Pasternack.Collections.Generic.Specialized.InternDictionary<string> internTable = new Pasternack.Collections.Generic.Specialized.InternDictionary<string>();

            BinaryReader reader = new BinaryReader(File.OpenRead(filename));
            int count = reader.ReadInt32();
            Dictionary<Pair<string, string>, double> result = new Dictionary<Pair<string, string>, double>(count);

            for (int i = 0; i < count; i++)
                result.Add(new Pair<string, string>(internTable.Intern(reader.ReadString()), internTable.Intern(reader.ReadString())), reader.ReadDouble());

            reader.Close();

            return result;
        }

        private class DoubleStringKeyValuePairConverter : IComparer<KeyValuePair<double, string>>
        {
            #region IComparer<KeyValuePair<double,string>> Members

            public int Compare(KeyValuePair<double, string> x, KeyValuePair<double, string> y)
            {
                return Math.Sign(y.Key - x.Key); //note that order is reversed
            }

            #endregion
        }

        private static void TestAlexAlignment(string alignmentFile, int maxSubstringLength)
        {
            DoubleStringKeyValuePairConverter doubleStringKeyValuePairConverter = new DoubleStringKeyValuePairConverter();

            int total = 0;
            int correct = 0;

            //FileStream fs = File.OpenRead(alignmentFile);
            //System.Runtime.Serialization.Formatters.Binary.BinaryFormatter bf = new System.Runtime.Serialization.Formatters.Binary.BinaryFormatter();
            //Dictionary<Pair<string,string>,double> probs = (Dictionary<Pair<string,string>,double>)bf.Deserialize(fs);
            ////Dictionary<string,long> totals = (Dictionary<string,long>)bf.Deserialize(fs);            
            //fs.Close();

            Dictionary<Pair<string, string>, double> probs = ReadProbDictionary(alignmentFile);

            Dictionary<string, List<string>> alexData = GetAlexData(@"C:\Users\jpaster2\Desktop\res\res\Russian\evalpairs.txt");
            List<string> alexWords = new List<string>(GetAlexWords().Keys);            

            foreach (KeyValuePair<string, List<string>> pair in alexData)
            {
                List<KeyValuePair<double, string>> top20 = new List<KeyValuePair<double, string>>();                

                foreach (string russian in alexWords)
                {
                    double prob = WikiTransliteration.GetAlignmentProbability(pair.Key, russian, maxSubstringLength, probs, top20.Count >= 20 ? top20[top20.Count-1].Key : 0,0);
                    if (top20.Count < 20 || top20[top20.Count - 1].Key < prob)
                    {
                        KeyValuePair<double,string> russianPair = new KeyValuePair<double,string>(prob,russian);
                        int index = top20.BinarySearch(russianPair, doubleStringKeyValuePairConverter);
                        if (index < 0) index = ~index; //complement if necessary                        
                        top20.Insert(index, russianPair);
                        if (top20.Count > 20) top20.RemoveAt(top20.Count - 1);
                    }
                }

                total++;

                bool correctFlag = false;
                foreach (KeyValuePair<double,string> russianPair in top20)
                    if (pair.Value.Contains(russianPair.Value))
                    {
                        correctFlag = true; correct++; break;
                    }

                if (!correctFlag)
                    Console.WriteLine("Missed " + pair.Key);
            }

            Console.WriteLine("Total: " + total);
            Console.WriteLine("Correct: " + correct);
            Console.WriteLine("Accuracy: " + (((double)correct) / total));
            Console.ReadLine();
        }

        private static void FindSynonyms(string redirectFile)
        {
            StreamReader reader = new StreamReader(redirectFile);
            WikiRedirectTable redirectTable = new WikiRedirectTable(reader);
            reader.Close();

            Dictionary<Pair<string, string>, long> counts = new Dictionary<Pair<string, string>, long>();

            Dictionary<string,List<string>> inverted = redirectTable.InvertedTable;
            foreach (KeyValuePair<string, List<string>> pair in inverted)
            {
                if (pair.Key.Length < 4 || !Regex.IsMatch(pair.Key,"^\\w+$",RegexOptions.Compiled)) continue;

                int maxDistance = pair.Key.Length / 4;
                List<string> synonyms = new List<string>();
                int alignmentLength;

                string lWord = pair.Key.ToLower();

                foreach (string syn in pair.Value)
                    if (!syn.Equals(pair.Key, StringComparison.OrdinalIgnoreCase) && WikiTransliteration.EditDistance<char>(lWord, syn.ToLower(), out alignmentLength) <= maxDistance)
                    {
                        synonyms.Add(syn);
                        //if (lWord.Length > syn.Length)
                        //    WikiTransliteration.CountAlignments(lWord, syn.ToLower(), 1, counts);
                    }

                if (synonyms.Count > 0)
                    Console.WriteLine(pair.Key + ": " + string.Join(", ", synonyms.ToArray()));
            }

            Console.ReadLine();
        }

        private static void CheckCoverage(string translationMapFile, params string[] xmlDataFiles)
        {
            List<KeyValuePair<string, string>> pairs = new List<KeyValuePair<string, string>>();
            foreach (string xmlDataFile in xmlDataFiles)
                pairs.AddRange(GetTaskPairs(xmlDataFile));

            int wordCount = 0;
            int coveredWords = 0;
            int correctWords = 0;
            int containedCorrectedWords = 0;

            Dictionary<Pasternack.Utility.Pair<string, string>, int> weights;
            Map<string, string> translationMap = ReadTranslationMap(translationMapFile, out weights);
            Map<string,string> flattenedTranslationMap = new Map<string,string>();
            foreach (KeyValuePair<string,string> tPair in translationMap)
                flattenedTranslationMap.TryAdd(StripAccent(tPair.Key),tPair.Value);

            foreach (KeyValuePair<string, string> pair in pairs)
            {
                string[] words = Regex.Split(pair.Key, "\\W", RegexOptions.Compiled);
                foreach (string word in words)
                {
                    if (word.Length == 0) continue;
                    string fWord = StripAccent(word);
                    wordCount++;
                    if (flattenedTranslationMap.ContainsKey(fWord))
                    {
                        coveredWords++;
                        if (((ICollection<string>)flattenedTranslationMap.GetValuesForKey(fWord)).Contains(pair.Value))
                            correctWords++;

                        foreach (string tWord in flattenedTranslationMap.GetValuesForKey(fWord))
                            if (tWord.Contains(pair.Value)) { containedCorrectedWords++; break; }
                    }
                }
            }

            Console.WriteLine("Word count: " + wordCount);
            Console.WriteLine("Covered words: " + coveredWords);
            Console.WriteLine("Correct words: " + correctWords);
            Console.WriteLine("Contained correct words: " + containedCorrectedWords);
            Console.ReadLine();
        }

        private static string GetMax(Dictionary<string, int> dict)
        {
            int highestValue = int.MinValue;
            string highestWord = null;
            foreach (KeyValuePair<string, int> pair in dict)
                if (pair.Value > highestValue)
                {
                    highestValue = pair.Value;
                    highestWord = pair.Key;
                }

            return highestWord;
        }

        static Dictionary<string, List<string>> GetAlexData(string path)
        {
            

            Dictionary<string, List<string>> result = new Dictionary<string, List<string>>();
            string[] data = File.ReadAllLines(path);
            foreach (string line in data)
            {
                if (line.Length == 0) continue;

                Match match = Regex.Match(line,"(?<eng>\\w+)\t(?<rroot>\\w+)(?: {(?:-(?<rsuf>\\w*?)(?:(?:, )|}))+)?",RegexOptions.Compiled);
                
                string russianRoot = match.Groups["rroot"].Value;
                if (russianRoot.Length == 0)
                    Console.WriteLine("Parse error");

                List<string> russianList = new List<string>();

                //if (match.Groups["rsuf"].Captures.Count == 0)
                    russianList.Add(russianRoot.ToLower()); //root only
                //else
                    foreach (Capture capture in match.Groups["rsuf"].Captures)
                        russianList.Add((russianRoot + capture.Value).ToLower());

                result[match.Groups["eng"].Value.ToLower()] = russianList;
                
            }

            return result;
        }

        static void AlexTestTable()
        {
            StreamDictionary<string, Dictionary<string, int>> sd = new StreamDictionary<string, Dictionary<string, int>>(
                                    100, 0.5, @"C:\Data\WikiTransliteration\translationTableKeys.dat", null, @"C:\Data\WikiTransliteration\transliterationTableValues.dat", null);

            Dictionary<string, List<string>> alexData = GetAlexData(@"C:\Users\jpaster2\Desktop\res\res\Russian\evalpairs.txt");

            double correct = 0;
            double total = 0;
            double found = 0;

            foreach (KeyValuePair<string,List<string>> pair in alexData)
            {
                string english = pair.Key;
                foreach (string name in pair.Value)
                {                                       
                    Dictionary<string, int> wordTable;
                    if (sd.TryGetValue(name, out wordTable) && wordTable.Count > 0)
                    {
                        found++;

                        string hypothesis = GetMax(wordTable);

                        bool foundEnglish = false;
                        foreach (string w in wordTable.Keys)
                            if (w == english)
                            {
                                foundEnglish = true;
                                break;
                            }
                            


                        if (foundEnglish)
                        {
                            correct++;
                            break;
                        }
                        else
                            Console.WriteLine("Bad translation found for " + name + "(" + hypothesis + "; should be " + english + ")");                        
                    }                                            
                }

                total++;                
            }

            Console.WriteLine("Found: " + found);
            Console.WriteLine("Correct: " + correct);
            Console.WriteLine("Total: " + total);
            Console.WriteLine("Accuracy of found names: " + (correct / found));
            Console.WriteLine("Accuracy: " + (correct / total));
            Console.WriteLine("Found: " + (found / total));
            Console.ReadLine();
        }

        static List<string> GetTaskWords(string xmlPath)
        {
            List<string> result = new List<string>();
            string input = File.ReadAllText(xmlPath);

            Regex regex = new Regex("<SourceName[^>]*>(?<english>.+?)</SourceName>", RegexOptions.IgnoreCase | RegexOptions.Compiled);
            Match match = regex.Match(input);

            while (match.Success)
            {                
                string english = match.Groups["english"].ToString().ToLower().Trim();

                result.Add(english);

                match = match.NextMatch();
            }

            return result;

        }

        static List<KeyValuePair<string, string>> GetTaskPairs(string xmlPath)
        {
            List<KeyValuePair<string, string>> result = new List<KeyValuePair<string, string>>();
            string input = File.ReadAllText(xmlPath);

            Regex regex = new Regex("<SourceName[^>]*>(?<english>.+?)</SourceName>\\s*<TargetName[^>]*>(?<name>.+?)</TargetName>", RegexOptions.IgnoreCase | RegexOptions.Compiled);
            Match match = regex.Match(input);

            while (match.Success)
            {
                string name = match.Groups["name"].ToString().ToLower().Trim();
                string english = match.Groups["english"].ToString().ToLower().Trim();

                result.Add(new KeyValuePair<string, string>(english, name));

                match = match.NextMatch();
            }

            return result;

        }

        static void TestTable()
        {
            StreamDictionary<string, Dictionary<string, int>> sd = new StreamDictionary<string, Dictionary<string, int>>(
                                    100, 0.5, @"C:\Data\WikiTransliteration\translationTableKeys.dat", null, @"C:\Data\WikiTransliteration\transliterationTableValues.dat", null);

            string data = File.ReadAllText(@"C:\Data\WikiTransliteration\NEWS09_train_EnRu_5977.xml");
            data += File.ReadAllText(@"C:\Data\WikiTransliteration\NEWS09_dev_EnRu_943.xml");

            double correct = 0;
            double total = 0;
            double found = 0;

            Regex regex = new Regex("<SourceName[^>]*>(?<english>.+?)</SourceName>\\w*<TargetName[^>]*>(?<name>.+?)</TargetName>", RegexOptions.IgnoreCase | RegexOptions.Compiled);
            Match match = regex.Match(data);

            while (match.Success)
            {
                string name = match.Groups["name"].ToString().ToLower().Trim();
                string english = match.Groups["english"].ToString().ToLower().Trim();

                Dictionary<string, int> wordTable;
                if (sd.TryGetValue(name, out wordTable) && wordTable.Count > 0)
                {
                    found++;

                    string hypothesis = GetMax(wordTable);
                    if (hypothesis == english)
                        correct++;
                    else
                        Console.WriteLine("Bad translation found for " + name + "(" + hypothesis + "; should be " + english + ")");                    
                }
                else
                    Console.WriteLine("No translation found for " + name + "(should be " + english + ")");

                total++;

                match = match.NextMatch();
            }

            Console.WriteLine("Found: " + found);
            Console.WriteLine("Correct: " + correct);
            Console.WriteLine("Total: " + total);
            Console.WriteLine("Accuracy of found names: " + (correct / found));
            Console.WriteLine("Accuracy: " + (correct / total));
            Console.WriteLine("Found: " + (found / total));
            Console.ReadLine();
        }

        static WikiLocalDB GetStubDB()
        {
            return new WikiLocalDB(
                                File.OpenRead(@"F:\Wikipedia_Stub\metadata.dat"),
                                new BetterBufferedStream(new FileStream(@"F:\Wikipedia_Stub\title_table.dat", FileMode.Open), 100000),
                                new BetterBufferedStream(new FileStream(@"F:\Wikipedia_Stub\page_offsets.dat", FileMode.Open), 1000000),
                                new BetterBufferedStream(new FileStream(@"F:\Wikipedia_Stub\page_records.dat", FileMode.Open), 5000000),
                                new BetterBufferedStream(new FileStream(@"F:\Wikipedia_Stub\revision_records.dat", FileMode.Open), 200000),
                                new BetterBufferedStream(new FileStream(@"F:\Wikipedia_Stub\revision_text_offset.dat", FileMode.Open), 200000, 16, true),
                                new BetterBufferedStream(new FileStream(@"F:\Wikipedia_Stub\revision_text.dat", FileMode.Open), 1000000),
                                new BetterBufferedStream(new FileStream(@"F:\Wikipedia_Stub\username_table.dat", FileMode.Open), 10000), 50000000, false);
        }

        static void MakeTable()
        {
            //Dictionary<string, List<string>> translations = new Dictionary<string, List<string>>();
            

            List<KeyValuePair<string, string>> russianNames = new List<KeyValuePair<string, string>>();

            StreamReader reader = new StreamReader(@"C:\Data\WikiTransliteration\translations.txt");
            while (!reader.EndOfStream)
            {
                string[] line = reader.ReadLine().Split('\t');

                if (line[0].StartsWith("en:", StringComparison.OrdinalIgnoreCase)) line[0] = line[0].Substring(3);
                if (line[1].StartsWith("ru:", StringComparison.OrdinalIgnoreCase)) line[1] = line[1].Substring(3);
                
                //remove parenthesized portion (if any))
                line[0] = Regex.Replace(line[0], "\\(.*\\)", "", RegexOptions.Compiled).Trim();
                line[1] = Regex.Replace(line[1], "\\(.*\\)", "", RegexOptions.Compiled).Trim();

                russianNames.Add(new KeyValuePair<string, string>(line[1], line[0]));
            }

            reader.Close();

            Dictionary<string, Dictionary<string, int>> translationTable = new Dictionary<string, Dictionary<string, int>>();

            foreach (KeyValuePair<string, string> pair in russianNames)
            {
                string[] russianWords = Regex.Split(pair.Key, "\\W", RegexOptions.Compiled);
                string[] englishWords = Regex.Split(pair.Value, "\\W", RegexOptions.Compiled);

                int score = 1;
                if (englishWords.Length == russianWords.Length) score = 2;

                foreach (string rawRussianWord in russianWords)
                {
                    if (rawRussianWord.Length == 0) continue;
                    string russianWord = rawRussianWord.ToLower();
                    if (!translationTable.ContainsKey(russianWord))
                        translationTable[russianWord] = new Dictionary<string, int>();

                    Dictionary<string, int> wordTable = translationTable[russianWord];

                    foreach (string rawEnglishWord in englishWords)
                    {
                        if (rawEnglishWord.Length == 0) continue;
                        string englishWord = rawEnglishWord.ToLower();
                        Dictionaries.IncrementOrSet<string>(wordTable, englishWord, score, score);
                    }
                }
            }

            StreamDictionary<string, Dictionary<string, int>> streamTable = new StreamDictionary<string, Dictionary<string, int>>(
                translationTable.Count * 2, 0.5, @"C:\Data\WikiTransliteration\translationTableKeys.dat", null, @"C:\Data\WikiTransliteration\transliterationTableValues.dat", null);

            foreach (KeyValuePair<string, Dictionary<string, int>> pair in translationTable)
                streamTable.Add(pair);

            streamTable.Close();
        }

        static void FindOverlap()
        {
            double found=0;
            double total=0;

            StreamReader fs = new StreamReader(@"C:\Data\WikiTransliteration\enRedirectTable.dat");
            WikiRedirectTable redirectTable = new WikiRedirectTable(fs);
            fs.Close();            

            Dictionary<string, List<string>> translations = new Dictionary<string, List<string>>();

            List<KeyValuePair<string, string>> russianNames = new List<KeyValuePair<string, string>>();

            StreamReader reader = new StreamReader(@"C:\Data\WikiTransliteration\translations.txt");
            while (!reader.EndOfStream)           
            {
                string[] line = reader.ReadLine().Split('\t');

                if (line[0].StartsWith("en:",StringComparison.OrdinalIgnoreCase)) line[0] = line[0].Substring(3);
                if (line[1].StartsWith("ru:",StringComparison.OrdinalIgnoreCase)) line[1] = line[1].Substring(3);

                russianNames.Add(new KeyValuePair<string,string>(line[1],line[0]));
            }
            
            reader.Close();            

            Dictionary<string, bool> dict = new Dictionary<string, bool>();
            foreach (KeyValuePair<string,string> rPair in russianNames)
            {
                //string[] words = rPair.Key.Split(' ', '\'', '.', ';', ':');
                //string[] english = rPair.Value.Split(' ', '\'', '.', ';', ':');

                //string englishString = rPair.Value;
                string englishString = redirectTable.Redirect(rPair.Value);
                englishString = Regex.Replace(englishString, "\\(.*\\)", "", RegexOptions.Compiled).Trim();

                string[] words = new string[] { rPair.Key };
                string[] english = new string[] { englishString };

                for (int i = 0; i < english.Length; i++) english[i] = english[i].ToLower().Trim();
                for (int i = 0; i < words.Length; i++) words[i] = words[i].ToLower().Trim();

                foreach (string word in words)
                {
                    if (word == "") continue;

                    dict[word.ToLower()] = true;
                    foreach (string eWord in english)
                    {
                        if (eWord.Length == 0) continue;

                        if (!translations.ContainsKey(word))
                            translations[word] = new List<string>();

                        if (!translations[word].Contains(eWord))
                            translations[word].Add(eWord);
                    }
                }
            }

            //filter out all but the word with length closest to the original English            
            foreach (KeyValuePair<string, List<string>> pair in translations)
            {
                string best = null;
                for (int i = 0; i < pair.Value.Count; i++)
                {
                    if (pair.Value[i].Contains(" "))
                    {
                        //pair.Value.RemoveAt(i);
                        //i--;
                        continue;
                    }

                    //pair.Value[i] = Regex.Replace(pair.Value[i], "\\W", "", RegexOptions.Compiled);

                    if (best == null || Math.Abs(pair.Key.Length - pair.Value[i].Length) < Math.Abs(pair.Key.Length - best.Length))
                        best = pair.Value[i];
                }

                pair.Value.Clear();
                if (best != null) pair.Value.Add(best);
            }

            string data = File.ReadAllText(@"C:\Data\WikiTransliteration\NEWS09_train_EnRu_5977.xml");
            data += File.ReadAllText(@"C:\Data\WikiTransliteration\NEWS09_dev_EnRu_943.xml");

            double correct = 0;

            Regex regex = new Regex("<SourceName[^>]*>(?<english>.+?)</SourceName>\\w*<TargetName[^>]*>(?<name>.+?)</TargetName>", RegexOptions.IgnoreCase | RegexOptions.Compiled);
            Match match = regex.Match(data);

            while (match.Success)
            {
                string name = match.Groups["name"].ToString().ToLower().Trim();
                string english = match.Groups["english"].ToString().ToLower().Trim();

                if (dict.ContainsKey(name))
                {
                    found++;
                    if (translations.ContainsKey(name) && translations[name].Contains(english))
                        correct++;
                    else
                    {
                        if (!translations.ContainsKey(name) || translations[name].Count == 0)
                            Console.WriteLine("No translation found for " + name + "(should be " + english + ")");
                        else
                            Console.WriteLine("Bad translation found for " + name + "(" + translations[name][0] + "; should be " + english + ")");
                    }
                }

                total++;

                match = match.NextMatch();
            }

            //StreamDictionary<string, string> sd = new StreamDictionary<string, string>(translations.Count * 2, 0.5, @"C:\data\WikiTransliteration\translationTable.dat", null, true, 255, 255, null, delegate(BinaryReader r) { return r.ReadString(); }, delegate(BinaryReader r) { return r.ReadString(); }, delegate(BinaryWriter w, string s) { w.Write(s); }, delegate(BinaryWriter w, string s) { w.Write(s); });            
            //foreach (KeyValuePair<string, List<string>> pair in translations)
            //{
            //    if (pair.Value.Count == 0) continue;
            //    sd[(pair.Key.Length > 250 ? pair.Key.Substring(0, 250) : pair.Key)] = pair.Value[0].Length > 250 ? pair.Value[0].Substring(0, 250) : pair.Value[0];
            //}

            //sd.Close();

            Console.WriteLine("Found: " + found);
            Console.WriteLine("Correct: " + correct);
            Console.WriteLine("Total: " + total);
            Console.WriteLine("Accuracy of found names: " + (correct / found));
            Console.WriteLine("Accuracy: " + (correct / total));
            Console.WriteLine("Found: " + (found / total));
            Console.ReadLine();
        }

        

        static void GetEntities(string wikiFile, string redirectTableFileName, bool english, TextWriter writer, bool useRedirects)
        {
            WikiRedirectTable redirectTable = null;
            
            StreamReader fs = new StreamReader(redirectTableFileName);
            redirectTable = new WikiRedirectTable(fs);
            fs.Close();
            
            Dictionary<string, List<string>> redirects = redirectTable.InvertedTable;

            ICSharpCode.SharpZipLib.BZip2.BZip2InputStream bzipped =
                new ICSharpCode.SharpZipLib.BZip2.BZip2InputStream(File.OpenRead(wikiFile));
            WikiXMLReader reader = new WikiXMLReader(bzipped);

            foreach (WikiPage page in reader.Pages)
            {
                if (WikiNamespace.GetNamespace(page.Title, reader.WikiInfo.Namespaces) != WikiNamespace.Default) continue;
                if (redirectTable.Redirect(page.Title) != page.Title) continue; //skip redirects

                foreach (WikiRevision revision in reader.Revisions)
                {
                    List<WikiLink> links = WikiLink.GetWikiLinks(revision.Text, true, false);
                    string other = null;
                    foreach (WikiLink link in links)                    
                        if (link.Target.StartsWith((english ? "ru:" : "en:"),StringComparison.OrdinalIgnoreCase))
                        {
                            other = WikiLink.GetTitleFromTarget(link.Target,reader.WikiInfo);
                            break;
                        }

                    if (other == null) continue; //no other language version
                    List<string> redirectTitles;
                    if (!useRedirects || !redirects.TryGetValue(page.Title, out redirectTitles))
                        redirectTitles = new List<string>();
                    if (!redirectTitles.Contains(page.Title)) redirectTitles.Add(page.Title);

                    foreach (string title in redirectTitles)
                    {
                        if (english)                        
                            writer.WriteLine(title + "\t" + other);
                        else
                            writer.WriteLine(other + "\t" + title);
                    }
                }
                
            }

            reader.Close();
        }
    }
}
