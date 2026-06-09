import { Text, View } from 'react-native';

import { ExternalLink } from './ExternalLink';
import { MonoText } from './StyledText';

export default function EditScreenInfo({ path }: { path: string }) {
  return (
    <View>
      <View className="mx-12 items-center">
        <Text className="text-center text-[17px] leading-6 text-slate-800">
          Open up the code for this screen:
        </Text>
        <View className="bg-slate-100 my-[7px] rounded px-1 dark:bg-white/5">
          <MonoText>{path}</MonoText>
        </View>
        <Text className="text-center text-[17px] leading-6 text-slate-800">
          Change any of the text, save the file, and your app will automatically update.
        </Text>
      </View>

      <View className="mx-5 mt-4 items-center">
        <ExternalLink className="py-4" href="https://docs.expo.io/get-started/create-a-new-app/#opening-the-app-on-your-phonetablet">
          <Text className="text-center text-sky-600">
            Tap here if your app doesn't automatically update after making changes
          </Text>
        </ExternalLink>
      </View>
    </View>
  );
}
